package aerocore.backend.config;

import aerocore.backend.dto.Nullable;
import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes the generated schemas say what the server really sends, so clients get precise types:
 * every component of a response record is required, and nullable when it is annotated with {@link Nullable}
 * or (outside *Input records) is a boxed number/boolean. This includes the *Input records: they are read and written by the same forms.
 * Only request wrappers (name ends with Request) keep the default shape; they declare required members themselves.
 */
@Component
public class OpenApiSchemaConverter implements ModelConverter {

    private static final String DTO_PACKAGE = "aerocore.backend.dto";
    private static final Set<Class<?>> BOXED = Set.of(Integer.class, Long.class, Double.class, Boolean.class);

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        Class<?> raw = rawClass(type.getType());
        if (resolved == null || raw == null || !raw.isRecord() || !raw.getName().startsWith(DTO_PACKAGE)
                || isRequestSide(raw)) {
            return resolved;
        }
        Schema model = context.getDefinedModels().get(modelName(resolved, raw));
        if (model == null || model.getProperties() == null) return resolved;

        Map<String, Schema> properties = model.getProperties();
        List<String> required = new ArrayList<>();
        for (RecordComponent c : raw.getRecordComponents()) {
            Schema property = properties.get(c.getName());
            if (property == null) continue;
            required.add(c.getName());
            if (c.isAnnotationPresent(Nullable.class) || (BOXED.contains(c.getType()) && !isInput(raw))) {
                properties.put(c.getName(), nullable(property));
            }
        }
        model.setRequired(required);
        return resolved;
    }

    /** *Input records are form models: a boxed type only detects a missing value, so only @Nullable means null. */
    private static boolean isInput(Class<?> raw) {
        return raw.getSimpleName().endsWith("Input");
    }

    private static boolean isRequestSide(Class<?> raw) {
        String name = raw.getSimpleName();
        return name.endsWith("Request");
    }

    private static String modelName(Schema resolved, Class<?> raw) {
        if (resolved.getName() != null) return resolved.getName();
        String ref = resolved.get$ref();
        if (ref != null) return ref.substring(ref.lastIndexOf('/') + 1);
        return raw.getSimpleName();
    }

    /** "type": ["string", "null"], or anyOf [$ref, null] for a reference (OpenAPI 3.1). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullable(Schema property) {
        if (property.get$ref() != null) {
            if (property.getAnyOf() != null) return property;
            Schema wrapper = new io.swagger.v3.oas.models.media.JsonSchema();
            List<Schema> options = new ArrayList<>();
            options.add(property);
            options.add(new io.swagger.v3.oas.models.media.JsonSchema().types(Set.of("null")));
            wrapper.setAnyOf(options);
            return wrapper;
        }
        property.addType("null");
        return property;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof JavaType jt) return jt.getRawClass();
        if (type instanceof Class<?> c) return c;
        return null;
    }
}
