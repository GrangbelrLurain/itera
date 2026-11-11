package aerocore.backend.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a response field that is present but may be null. The generated API document lists every field of a
 * response record as required, and as nullable when this annotation is present or the type is a boxed number
 * or boolean (see OpenApiSchemaConverter). Records named *Input / *Request are left untouched: they are the
 * request side, where the service validates the values.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD})
public @interface Nullable {
}
