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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "memory" profile starts the whole API without a database: sample data is present, a booking holds seats that
 * the admin departure calendar sees, and the reset hook puts everything back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("memory")
class MemoryProfileTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    MockMvc mvc;

    private String adminAuth() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/admin/login").contentType(JSON)
                        .content("{\"username\":\"admin\",\"password\":\"e2e-admin-password\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    private String firstDeparture() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/packages").param("keyword", "DAD501"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.items[0].departureDate");
    }

    @Test
    @DisplayName("Sample data is served, a booking is visible in the admin calendar, reset removes it")
    void sampleDataBookingAndReset() throws Exception {
        mvc.perform(post("/api/test/reset")).andExpect(status().isNoContent());
        String auth = adminAuth();

        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        String date = firstDeparture();
        String body = "{\"productCode\":\"DAD501\",\"departureDate\":\"" + date + "\",\"channelCode\":\"AGENCY_A\","
                + "\"contact\":{\"name\":\"Tester\",\"phone\":\"010-0000-0000\"},"
                + "\"travelers\":[{\"targetType\":\"ADULT\",\"name\":\"Tester\",\"birthDate\":\"1985-05-05\",\"gender\":\"M\"}]}";
        mvc.perform(post("/api/v1/package-bookings").contentType(JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/admin/products/DAD501/departures").param("from", date).param("to", date)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reservedSeats").value(1))
                .andExpect(jsonPath("$[0].stored").value(true));
        mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$.statusCounts.PENDING_PAYMENT").value(1));

        mvc.perform(post("/api/test/reset")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/products/DAD501/departures").param("from", date).param("to", date)
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(jsonPath("$[0].reservedSeats").value(0))
                .andExpect(jsonPath("$[0].stored").value(false));
    }

    @Test
    @DisplayName("A closed departure refuses bookings and reopening accepts them again")
    void closedDepartureRefusesBookings() throws Exception {
        mvc.perform(post("/api/test/reset")).andExpect(status().isNoContent());
        String auth = adminAuth();
        String date = firstDeparture();
        String body = "{\"productCode\":\"DAD501\",\"departureDate\":\"" + date + "\","
                + "\"contact\":{\"name\":\"Tester\",\"phone\":\"010-0000-0000\"},"
                + "\"travelers\":[{\"targetType\":\"ADULT\",\"name\":\"Tester\",\"birthDate\":\"1985-05-05\",\"gender\":\"M\"}]}";

        mvc.perform(post("/api/v1/admin/products/DAD501/departures/" + date + "/close")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/package-bookings").contentType(JSON).content(body))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/admin/products/DAD501/departures/" + date + "/reopen")
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/package-bookings").contentType(JSON).content(body))
                .andExpect(status().isCreated());
    }
}
