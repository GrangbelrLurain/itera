package aerocore.backend;

import aerocore.backend.TestFixtures.FakeAdmin;
import aerocore.backend.TestFixtures.FakeCommissions;
import aerocore.backend.TestFixtures.FakeProducts;
import aerocore.backend.TestFixtures.MutableClock;
import aerocore.backend.model.ProductData;
import aerocore.backend.repository.InMemoryPackageBookingRepository;
import aerocore.backend.service.AdminAuthService;
import aerocore.backend.service.AdminPricingService;
import aerocore.backend.service.AdminProductService;
import aerocore.backend.service.AdminService;
import aerocore.backend.service.CostBasedPriceCalculator;
import aerocore.backend.service.PackageBookingService;
import aerocore.backend.service.PackageService;
import aerocore.backend.service.PriceCalculator;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static aerocore.backend.TestFixtures.cost;
import static aerocore.backend.TestFixtures.data;
import static aerocore.backend.TestFixtures.product;
import static aerocore.backend.TestFixtures.rule;
import static aerocore.backend.TestFixtures.withPricing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level test of the package, booking and admin controllers: routing, JSON binding, status codes and the
 * bearer-token check, on top of in-memory repositories (no database).
 */
@SpringBootTest(properties = {"aerocore.admin.password=test-pass", "aerocore.admin.token-secret=test-secret"})
@AutoConfigureMockMvc
@Import(HttpApiTest.Fakes.class)
class HttpApiTest {

    /** The services of DatabaseConfig, wired on fake repositories. */
    @TestConfiguration
    static class Fakes {
        private final MutableClock clock = new MutableClock(TestFixtures.TODAY);

        @Bean
        Clock testClock() {
            return clock;
        }

        @Bean
        FakeProducts products() {
            ProductData d = withPricing(
                    data(product("DAD501", "다낭 호이안", 10),
                            List.of(rule(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 5, 31), DayOfWeek.WEDNESDAY))),
                    2500, 0, cost(1, "FLIGHT", "ADULT", 520000), cost(2, "HOTEL", "ADULT", 300000),
                    cost(3, "LAND", "ADULT", 180000));
            return new FakeProducts().add(d);
        }

        @Bean
        FakeAdmin productAdmin(FakeProducts products) {
            return new FakeAdmin(products);
        }

        @Bean
        FakeCommissions commissions() {
            return new FakeCommissions().add("AGENCY_A", null, 800, 0, true);
        }

        @Bean
        InMemoryPackageBookingRepository packageBookings() {
            return new InMemoryPackageBookingRepository();
        }

        @Bean
        PriceCalculator priceCalculator() {
            return new CostBasedPriceCalculator();
        }

        @Bean
        PackageService packageService(FakeProducts products, PriceCalculator calc) {
            return new PackageService(products, calc, clock);
        }

        @Bean
        PackageBookingService packageBookingService(FakeProducts products, InMemoryPackageBookingRepository bookings,
                                                    PriceCalculator calc, FakeCommissions commissions) {
            return new PackageBookingService(products, bookings, calc, commissions, clock);
        }

        @Bean
        AdminAuthService adminAuthService() {
            return new AdminAuthService("admin", "test-pass", "test-secret", clock);
        }

        @Bean
        AdminService adminService(PackageBookingService bookings, InMemoryPackageBookingRepository repo,
                                  PackageService packages) {
            return new AdminService(bookings, repo, packages, clock);
        }

        @Bean
        AdminProductService adminProductService(FakeProducts products, FakeAdmin admin) {
            return new AdminProductService(products, admin, clock);
        }

        @Bean
        AdminPricingService adminPricingService(FakeProducts products, FakeAdmin admin, FakeCommissions commissions,
                                                PriceCalculator calc) {
            return new AdminPricingService(products, admin, commissions, calc, clock);
        }
    }

    @Autowired
    MockMvc mvc;

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private String login() throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/admin/login").contentType(JSON)
                        .content("{\"username\":\"admin\",\"password\":\"test-pass\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.token");
    }

    @Test
    @DisplayName("Public package list and detail: 200, invalid date 400, unknown product 404")
    void testPackageEndpoints() throws Exception {
        mvc.perform(get("/api/v1/packages").param("from", "2027-03-01").param("to", "2027-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productCode").value("DAD501"));
        mvc.perform(get("/api/v1/packages/DAD501/departures/2027-03-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basic.productCode").value("DAD501"))
                .andExpect(jsonPath("$.prices[0].salePrice").value(1250000));
        mvc.perform(get("/api/v1/packages/DAD501/departures/not-a-date"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mvc.perform(get("/api/v1/packages/NOPE/departures/2027-03-10"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Package booking: create 201, read, pay, unknown code 404, broken JSON 400")
    void testPackageBookingFlow() throws Exception {
        String body = "{\"productCode\":\"DAD501\",\"departureDate\":\"2027-03-10\",\"channelCode\":\"AGENCY_A\","
                + "\"contact\":{\"name\":\"홍길동\",\"phone\":\"010-1234-5678\"},"
                + "\"travelers\":[{\"targetType\":\"ADULT\",\"name\":\"홍길동\",\"birthDate\":\"1985-05-05\",\"gender\":\"M\"}]}";
        MvcResult created = mvc.perform(post("/api/v1/package-bookings").contentType(JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.seatCount").value(1))
                .andReturn();
        String code = JsonPath.read(created.getResponse().getContentAsString(), "$.bookingCode");

        mvc.perform(get("/api/v1/package-bookings/" + code)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/package-bookings/" + code + "/pay").contentType(JSON)
                        .content("{\"paymentReference\":\"PAY-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(get("/api/v1/package-bookings/PB-MISSING")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/package-bookings").contentType(JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mvc.perform(post("/api/v1/package-bookings").contentType(JSON)
                        .content("{\"productCode\":\"DAD501\",\"departureDate\":\"2027-03-11\",\"travelers\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin API: no or wrong token 401, login, dashboard, product list")
    void testAdminAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/login").contentType(JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        String auth = "Bearer " + login();
        mvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/products").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("DAD501"));
        mvc.perform(get("/api/v1/admin/nothing").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CORS preflight of an admin route needs no token")
    void testPreflight() throws Exception {
        mvc.perform(options("/api/v1/admin/products").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    @DisplayName("Admin pricing: cost override round trip changes the public price of that date only")
    void testCostOverrideThroughHttp() throws Exception {
        String auth = "Bearer " + login();
        mvc.perform(put("/api/v1/admin/products/dad501/departures/2027-03-17/cost-overrides")
                        .header(HttpHeaders.AUTHORIZATION, auth).contentType(JSON)
                        .content("{\"rows\":[{\"componentType\":\"HOTEL\",\"targetType\":\"ADULT\",\"cost\":450000,\"note\":\"peak\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrides[0].cost").value(450000));
        mvc.perform(get("/api/v1/packages/DAD501/departures/2027-03-17"))
                .andExpect(jsonPath("$.prices[0].salePrice").value(1437500));      // (520+450+180)k * 1.25
        mvc.perform(get("/api/v1/packages/DAD501/departures/2027-03-10"))
                .andExpect(jsonPath("$.prices[0].salePrice").value(1250000));      // other dates unchanged

        mvc.perform(put("/api/v1/admin/products/DAD501/departures/2027-03-17/cost-overrides")
                        .header(HttpHeaders.AUTHORIZATION, auth).contentType(JSON).content("{\"rows\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overrides").isEmpty());
        mvc.perform(get("/api/v1/packages/DAD501/departures/2027-03-17"))
                .andExpect(jsonPath("$.prices[0].salePrice").value(1250000));

        mvc.perform(put("/api/v1/admin/products/DAD501/departures/2027-03-11/cost-overrides")
                        .header(HttpHeaders.AUTHORIZATION, auth).contentType(JSON).content("{\"rows\":[]}"))
                .andExpect(status().isNotFound());                                  // not a departure date
        mvc.perform(put("/api/v1/admin/products/DAD501/departures/2027-03-17/cost-overrides")
                        .header(HttpHeaders.AUTHORIZATION, auth).contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));          // rows is required
        mvc.perform(put("/api/v1/admin/products/DAD501/departures/bad/cost-overrides")
                        .header(HttpHeaders.AUTHORIZATION, auth).contentType(JSON).content("{\"rows\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
