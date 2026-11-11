package aerocore.backend;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settings of a public demo: the unauthenticated reset endpoint is not there unless asked for, and the data goes
 * back to the sample state on its own at the configured interval.
 */
class MemoryDemoModeTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private static String adminToken(MockMvc mvc) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/admin/login").contentType(JSON)
                        .content("{\"username\":\"admin\",\"password\":\"e2e-admin-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("memory")
    static class ResetEndpointOff {
        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("Without the opt-in there is no reset endpoint (the data cannot be wiped by anyone)")
        void resetEndpointIsAbsentByDefault() throws Exception {
            mvc.perform(post("/api/test/reset")).andExpect(status().isNotFound());
        }
    }

    @SpringBootTest(properties = "aerocore.memory.reset-interval=PT1S")
    @AutoConfigureMockMvc
    @ActiveProfiles("memory")
    static class ScheduledReset {
        @Autowired
        MockMvc mvc;

        @Test
        @DisplayName("A booking disappears by itself when the reset interval passes")
        void dataIsResetOnSchedule() throws Exception {
            String auth = adminToken(mvc);
            MvcResult list = mvc.perform(get("/api/v1/packages").param("keyword", "DAD501"))
                    .andExpect(status().isOk()).andReturn();
            String date = JsonPath.read(list.getResponse().getContentAsString(), "$.items[0].departureDate");
            String body = "{\"productCode\":\"DAD501\",\"departureDate\":\"" + date + "\","
                    + "\"contact\":{\"name\":\"Tester\",\"phone\":\"010-0000-0000\"},"
                    + "\"travelers\":[{\"targetType\":\"ADULT\",\"name\":\"Tester\",\"birthDate\":\"1985-05-05\",\"gender\":\"M\"}]}";
            mvc.perform(post("/api/v1/package-bookings").contentType(JSON).content(body)).andExpect(status().isCreated());

            Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
            int pending = 1;
            while (pending > 0 && Instant.now().isBefore(deadline)) {
                Thread.sleep(250);
                MvcResult dash = mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
                        .andExpect(status().isOk()).andReturn();
                pending = countPending(dash.getResponse().getContentAsString());
            }
            assertTrue(pending == 0, "the booking should have been cleared by the scheduled reset");
        }

        private static int countPending(String json) {
            java.util.Map<String, Object> counts = JsonPath.read(json, "$.statusCounts");
            Object v = counts.get("PENDING_PAYMENT");
            return v == null ? 0 : ((Number) v).intValue();
        }
    }
}
