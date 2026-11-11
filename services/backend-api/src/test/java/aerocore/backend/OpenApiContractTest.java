package aerocore.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API contract is generated from the controllers. This test compares the generated documents with the
 * committed copies in contracts/ so a controller change cannot go out without the contract (and the clients
 * generated from it). Run {@code ./gradlew exportApiDocs} to refresh the files.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Path CONTRACTS = Path.of(System.getProperty("contracts.dir", "../../contracts"));
    private static final boolean UPDATE = Boolean.getBoolean("contracts.update");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    MockMvc mvc;

    private void check(String group) throws Exception {
        String raw = mvc.perform(get("/v3/api-docs/" + group)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        // parse into maps so the keys are written in a stable (sorted) order
        // Jackson indents with the platform line separator; keep the files LF on every OS
        String generated = MAPPER.writeValueAsString(MAPPER.readValue(raw, Object.class)).replace("\r\n", "\n") + "\n";
        Path file = CONTRACTS.resolve(group + ".openapi.json");
        if (UPDATE) {
            Files.createDirectories(CONTRACTS);
            Files.writeString(file, generated, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(file), file + " is missing: run ./gradlew exportApiDocs");
        String committed = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(committed, generated, file + " is out of date: run ./gradlew exportApiDocs and commit it");
    }

    @Test
    @DisplayName("contracts/public.openapi.json matches the public controllers")
    void publicDocumentIsUpToDate() throws Exception {
        check("public");
    }

    @Test
    @DisplayName("contracts/admin.openapi.json matches the admin controllers")
    void adminDocumentIsUpToDate() throws Exception {
        check("admin");
    }

    @Test
    @DisplayName("Response schemas are precise: required fields, nullable fields, enums and the common error body")
    void schemasAreRequiredAndNullablePrecise() throws Exception {
        String raw = mvc.perform(get("/v3/api-docs/admin")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode doc = MAPPER.readTree(raw);
        com.fasterxml.jackson.databind.JsonNode booking = doc.at("/components/schemas/PackageBooking");
        assertTrue(booking.at("/required").toString().contains("\"status\""), "status is always present");
        assertTrue(booking.at("/required").toString().contains("\"cancelReason\""), "nullable fields stay required");
        assertEquals("[\"string\",\"null\"]", booking.at("/properties/cancelReason/type").toString());
        assertEquals("string", booking.at("/properties/status/type").asText());
        assertEquals(4, booking.at("/properties/status/enum").size());
        // a request body is not made "required" by the converter: only the @NotNull members are
        assertEquals("[\"rows\"]", doc.at("/components/schemas/CostRowsRequest/required").toString());
        // every operation documents the common error body; the protected ones also the 401
        assertEquals("#/components/schemas/ErrorResponse",
                doc.at("/paths/~1api~1v1~1admin~1dashboard/get/responses/default/content/application~1json/schema/$ref").asText());
        assertTrue(doc.at("/paths/~1api~1v1~1admin~1dashboard/get/responses").has("401"));
        assertTrue(!doc.at("/paths/~1api~1v1~1admin~1login/post/responses").has("401"), "login needs no token");
    }
}
