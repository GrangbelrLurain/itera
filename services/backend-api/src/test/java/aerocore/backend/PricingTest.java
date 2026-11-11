package aerocore.backend;

import aerocore.backend.dto.AdminDtos.AdminBookingDetail;
import aerocore.backend.dto.AdminPricingDtos.CommissionInput;
import aerocore.backend.dto.AdminPricingDtos.CostInput;
import aerocore.backend.dto.AdminPricingDtos.PreviewRow;
import aerocore.backend.dto.AdminPricingDtos.PricingInput;
import aerocore.backend.dto.AdminPricingDtos.PricingPreview;
import aerocore.backend.dto.AdminPricingDtos.PricingView;
import aerocore.backend.dto.PackageBookingDtos.Contact;
import aerocore.backend.dto.PackageBookingDtos.CreateRequest;
import aerocore.backend.dto.PackageBookingDtos.TravelerInput;
import aerocore.backend.exception.PackageNotFoundException;
import aerocore.backend.exception.ValidationException;
import aerocore.backend.model.DepartureRow;
import aerocore.backend.model.PackageBookingData;
import aerocore.backend.model.ProductData;
import aerocore.backend.repository.InMemoryPackageBookingRepository;
import aerocore.backend.service.AdminPricingService;
import aerocore.backend.service.CostBasedPriceCalculator;
import aerocore.backend.service.PackageBookingService;
import aerocore.backend.service.PriceCalculator;
import aerocore.backend.service.PricingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static aerocore.backend.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Cost-based pricing: arithmetic, calculator, commissions, booking snapshot and admin management. */
public class PricingTest {

    private static final LocalDate DEPARTURE = LocalDate.of(2027, 3, 10);   // Wednesday, inside the rule
    private final CostBasedPriceCalculator calculator = new CostBasedPriceCalculator();

    /** Product with fuel 90,000 / taxes 45,000 pass-through and a 50,000 flat discount (not for infants). */
    private ProductData priced(int marginBp, long marginFixed, ProductData.CostRow... costs) {
        ProductData d = data(product("DAD501", "다낭", 10), List.of(rule(LocalDate.of(2027, 3, 1),
                LocalDate.of(2027, 5, 31), DayOfWeek.WEDNESDAY)));
        return costs.length == 0 ? d : withPricing(d, marginBp, marginFixed, costs);
    }

    public static void main(String[] args) {
        PricingTest t = new PricingTest();
        t.testSalePriceFromCostFollowsTheDesignExamples();
        t.testCommissionArithmetic();
        t.testCalculatorUsesCostWhenAvailableAndManualPriceOtherwise();
        t.testCalculatorPassThroughDiscountAndEdgeCases();
        t.testCommissionPolicyPrecedence();
        t.testBookingStoresCostAndCommissionSnapshot();
        t.testAdminBookingDetailShowsProfitOnlyWhenCostIsKnown();
        t.testSavePricingValidation();
        t.testPreviewShowsMarginCommissionAndProfit();
        t.testChannelCommissionTableValidationAndReplace();
        t.testCostOverridesReplaceOrAddCostLinesForOneDateOnly();
        t.testOverrideCanMakeAManuallyPricedTypeCostBasedForOneDate();
        t.testBookingSnapshotUsesTheCostOfItsDeparture();
        t.testSaveCostOverridesValidationAndStorageRule();
        t.testPreviewForADateAndCalendarFlags();
        System.out.println("✔ PricingTest all assertions passed!");
    }

    @Test
    @DisplayName("Sale price = truncate100(cost + cost*bp/10000 + fixed): the design document examples")
    public void testSalePriceFromCostFollowsTheDesignExamples() {
        assertEquals(1120000L, PricingRules.salePrice(1000000, 1200, 0));        // 12 % mark-up
        assertEquals(916600L, PricingRules.salePrice(833333, 1000, 0));          // 916,666 -> 916,600
        assertEquals(1050000L, PricingRules.salePrice(1000000, 0, 50000));       // fixed margin only
        assertEquals(1150000L, PricingRules.salePrice(1000000, 1000, 50000));    // both add up
        assertEquals(1000000L, PricingRules.salePrice(1000000, 0, 0));           // no margin: sale = cost
        assertEquals(0L, PricingRules.salePrice(0, 0, 0));
        assertEquals(0L, PricingRules.salePrice(99, 0, 0));                      // below the rounding unit
        assertEquals(100L, PricingRules.salePrice(99, 0, 1));                    // 100 exactly
        assertEquals(2500000L, PricingRules.salePrice(1000000, 15000, 0));       // 150 % mark-up
        assertEquals(1002500L, PricingRules.salePrice(1000000, 25, 0));          // 0.25 % -> 2,500
        assertEquals(1003500L, PricingRules.salePrice(1000999, 25, 0));          // 1,000,999 + 2,502 = 1,003,501
        assertEquals(100L, PricingRules.truncate(199));
        assertEquals(0L, PricingRules.truncate(-5));
        assertEquals(11_000_000_000L, PricingRules.salePrice(1_000_000_000L, 100_000, 0));   // no overflow at the limits
    }

    @Test
    @DisplayName("Commission = afterDiscountSale*bp/10000 + fixed, capped at the sale price, truncated to 100 won")
    public void testCommissionArithmetic() {
        assertEquals(85600L, PricingRules.commission(1070000, 800, 0));          // 8 %
        assertEquals(37000L, PricingRules.commission(1234567, 300, 0));          // 37,037 -> 37,000
        assertEquals(20000L, PricingRules.commission(1000000, 0, 20000));        // fixed only
        assertEquals(120000L, PricingRules.commission(1000000, 1000, 20000));    // both add up
        assertEquals(1000000L, PricingRules.commission(1000000, 10000, 500));    // never more than the sale price
        assertEquals(0L, PricingRules.commission(1000000, 0, 0));
        assertEquals(0L, PricingRules.commission(0, 800, 0));
        assertEquals(0L, PricingRules.commission(50, 800, 0));                   // 4 won -> 0
    }

    @Test
    @DisplayName("Calculator: traveler types with cost rows are priced from cost, others keep the manual price")
    public void testCalculatorUsesCostWhenAvailableAndManualPriceOtherwise() {
        // adult cost 520,000 + 300,000 + 180,000 = 1,000,000, mark-up 25 %
        ProductData d = priced(2500, 0, cost(1, "FLIGHT", "ADULT", 520000), cost(2, "HOTEL", "ADULT", 300000),
                cost(3, "LAND", "ADULT", 180000));
        PriceCalculator.Quote adult = calculator.quote(d, DEPARTURE, "ADULT");
        assertEquals(1000000L, adult.cost().longValue());
        assertEquals(1250000L, adult.salePrice());                              // 1,000,000 * 1.25
        assertEquals(250000L, adult.margin().longValue());
        assertEquals(50000L, adult.discount());
        assertEquals(1200000L, adult.afterDiscount());
        assertEquals(135000L, adult.passThrough());                              // fuel 90,000 + taxes 45,000
        assertEquals(1335000L, adult.payable());
        assertEquals(1385000L, adult.totalBeforeDiscount());

        PriceCalculator.Quote child = calculator.quote(d, DEPARTURE, "NOBED_CHILD");   // no cost rows -> manual
        assertNull(child.cost());
        assertNull(child.margin());
        assertEquals(900000L, child.salePrice());                                // the stored base price
        assertEquals(900000L - 50000L + 135000L, child.payable());

        // without any pricing data the calculator behaves exactly like the manual price list
        ProductData manual = priced(0, 0);
        assertNull(manual.pricing());
        assertEquals(1000000L, calculator.quote(manual, DEPARTURE, "ADULT").salePrice());
        assertNull(calculator.quote(manual, DEPARTURE, "ADULT").cost());
    }

    @Test
    @DisplayName("Calculator: fuel/taxes are never marked up, infants get no discount, discount is capped, unknown type is null")
    public void testCalculatorPassThroughDiscountAndEdgeCases() {
        ProductData d = priced(5000, 0, cost(1, "FLIGHT", "ADULT", 400000), cost(2, "FLIGHT", "INFANT", 100000));
        PriceCalculator.Quote adult = calculator.quote(d, DEPARTURE, "ADULT");
        assertEquals(600000L, adult.salePrice());                                // 400,000 * 1.5
        assertEquals(135000L, adult.passThrough());                              // unchanged by the 50 % mark-up
        PriceCalculator.Quote infant = calculator.quote(d, DEPARTURE, "INFANT");
        assertEquals(150000L, infant.salePrice());
        assertEquals(0L, infant.discount());                                     // infants: no discount
        assertEquals(165000L, infant.payable());                                 // 150,000 + 15,000 taxes
        assertNull(calculator.quote(d, DEPARTURE, "SENIOR"));

        // a discount larger than the sale price is capped at the sale price
        ProductData cheap = new ProductData(product("CHEAP", "싼 상품", 10, true, 900000),
                List.of(new ProductData.Price("ADULT", 100000, 0, 0, true)), List.of(), java.util.Set.of(), List.of(),
                List.of(), List.of(), null, null);
        PriceCalculator.Quote q = calculator.quote(cheap, DEPARTURE, "ADULT");
        assertEquals(100000L, q.discount());
        assertEquals(0L, q.afterDiscount());
        assertEquals(0L, q.payable());
    }

    @Test
    @DisplayName("Commission policy: the product row beats the channel's all-products row; inactive rows are ignored")
    public void testCommissionPolicyPrecedence() {
        FakeCommissions c = new FakeCommissions()
                .add("AGENCY_B", null, 500, 0, true)
                .add("AGENCY_B", "DAD501", 1000, 0, true)
                .add("AGENCY_A", null, 800, 0, false)           // inactive
                .add("PARTNER_C", null, 0, 20000, true);
        assertEquals(1000, c.resolve("AGENCY_B", "DAD501").orElseThrow().commissionBp());
        assertEquals(500, c.resolve("AGENCY_B", "TYO201").orElseThrow().commissionBp());   // falls back to the channel row
        assertTrue(c.resolve("AGENCY_A", "DAD501").isEmpty());                              // inactive
        assertTrue(c.resolve("NOBODY", "DAD501").isEmpty());
        assertEquals(20000L, c.resolve("PARTNER_C", "DAD501").orElseThrow().commissionFixed());
        c.add("AGENCY_B", "DAD501", 1500, 0, false);                                        // inactive product row
        assertEquals(1000, c.resolve("AGENCY_B", "DAD501").orElseThrow().commissionBp());   // the first active one wins
    }

    private PackageBookingService bookingService(ProductData d, FakeCommissions commissions,
                                                 InMemoryPackageBookingRepository bookings) {
        FakeProducts store = new FakeProducts().add(d);
        return new PackageBookingService(store, bookings, calculator, commissions, new MutableClock(TODAY));
    }

    private static CreateRequest request(String channel, TravelerInput... travelers) {
        return new CreateRequest("DAD501", "2027-03-10", channel, new Contact("홍길동", "010-1234-5678", null),
                List.of(travelers));
    }

    private static TravelerInput adult(String name) {
        return new TravelerInput("ADULT", name, "1990-05-01", "M");
    }

    @Test
    @DisplayName("Booking: each traveler row snapshots cost and the channel commission at booking time")
    public void testBookingStoresCostAndCommissionSnapshot() {
        ProductData d = priced(2500, 0, cost(1, "FLIGHT", "ADULT", 520000), cost(2, "HOTEL", "ADULT", 300000),
                cost(3, "LAND", "ADULT", 180000));
        FakeCommissions commissions = new FakeCommissions().add("AGENCY_A", null, 800, 0, true);
        InMemoryPackageBookingRepository bookings = new InMemoryPackageBookingRepository();
        PackageBookingService service = bookingService(d, commissions, bookings);

        String code = service.create(request("AGENCY_A", adult("A"),
                new TravelerInput("NOBED_CHILD", "C", "2018-01-01", "F"))).bookingCode();
        List<PackageBookingData.Traveler> t = bookings.findByCode(code).orElseThrow().travelers();
        // adult: cost 1,000,000 -> sale 1,250,000, after discount 1,200,000, commission 8 % = 96,000
        assertEquals(1000000L, t.get(0).unitCost().longValue());
        assertEquals(1250000L, t.get(0).unitSalePrice());
        assertEquals(96000L, t.get(0).unitCommission().longValue());
        assertEquals(1335000L, t.get(0).unitPrice());                    // the customer pays sale - discount + pass-through
        // no-bed child is a manual price: cost unknown, commission still computed from the sale price
        assertNull(t.get(1).unitCost());
        assertEquals(68000L, t.get(1).unitCommission().longValue());     // 8 % of (900,000 - 50,000) = 68,000

        // DIRECT or a channel without a policy: commission 0, same customer price
        String direct = service.create(request(null, adult("B"))).bookingCode();
        PackageBookingData.Traveler d0 = bookings.findByCode(direct).orElseThrow().travelers().get(0);
        assertEquals(0L, d0.unitCommission().longValue());
        assertEquals(1335000L, d0.unitPrice());
        String unknown = service.create(request("NEW_CHANNEL", adult("C"))).bookingCode();
        assertEquals(0L, bookings.findByCode(unknown).orElseThrow().travelers().get(0).unitCommission().longValue());

        // policy changes later do not touch existing snapshots
        commissions.rows.clear();
        assertEquals(96000L, bookings.findByCode(code).orElseThrow().travelers().get(0).unitCommission().longValue());
    }

    @Test
    @DisplayName("Admin booking detail: total cost, commission and profit; profit is unknown with manual prices")
    public void testAdminBookingDetailShowsProfitOnlyWhenCostIsKnown() {
        ProductData d = priced(2500, 0, cost(1, "FLIGHT", "ADULT", 1000000));
        FakeCommissions commissions = new FakeCommissions().add("AGENCY_A", null, 800, 0, true);
        InMemoryPackageBookingRepository bookings = new InMemoryPackageBookingRepository();
        PackageBookingService service = bookingService(d, commissions, bookings);

        AdminBookingDetail two = service.getForAdmin(service.create(request("AGENCY_A", adult("A"), adult("B"))).bookingCode());
        assertEquals(2, two.finance().size());
        assertEquals(2000000L, two.totalCost().longValue());
        assertEquals(192000L, two.totalCommission());                     // 2 x 96,000
        // profit per adult: 1,200,000 - 1,000,000 - 96,000 = 104,000
        assertEquals(208000L, two.profit().longValue());

        AdminBookingDetail mixed = service.getForAdmin(service.create(request("AGENCY_A", adult("A"),
                new TravelerInput("NOBED_CHILD", "C", "2018-01-01", "F"))).bookingCode());
        assertNull(mixed.totalCost());                                    // one manually priced traveler
        assertNull(mixed.profit());
        assertTrue(mixed.totalCommission() > 0);
    }

    private AdminPricingService adminService(ProductData d, FakeCommissions commissions) {
        FakeProducts store = new FakeProducts().add(d);
        return new AdminPricingService(store, new FakeAdmin(store), commissions, calculator, new MutableClock(TODAY));
    }

    private static CostInput costInput(String component, String target, Long cost) {
        return new CostInput(component, target, cost, null);
    }

    @Test
    @DisplayName("Save pricing: component/target/amount/margin validation; a cost target needs a price row")
    public void testSavePricingValidation() {
        AdminPricingService svc = adminService(priced(0, 0), new FakeCommissions());
        PricingView ok = svc.savePricing("DAD501", new PricingInput(
                List.of(costInput("FLIGHT", "ADULT", 520000L), costInput("HOTEL", "ADULT", 300000L)), 2500, 10000L));
        assertEquals(2, ok.costs().size());
        assertEquals(2500, ok.marginBp());
        assertEquals(10000L, ok.marginFixed());
        assertEquals(2, svc.getPricing("DAD501").costs().size());

        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(costInput("MAGIC", "ADULT", 1L)), 0, 0L)));                        // component
        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(costInput("FLIGHT", "SENIOR", 1L)), 0, 0L)));                      // target
        // the traveler type must already have a price row (it carries fuel surcharge and taxes)
        ProductData noInfant = new ProductData(product("DAD501", "다낭", 10),
                List.of(new ProductData.Price("ADULT", 1000000, 90000, 45000, true)), List.of(), java.util.Set.of(),
                List.of(), List.of(), List.of(), null, null);
        AdminPricingService limited = adminService(noInfant, new FakeCommissions());
        assertThrows(ValidationException.class, () -> limited.savePricing("DAD501", new PricingInput(
                List.of(costInput("FLIGHT", "INFANT", 1L)), 0, 0L)));
        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(costInput("FLIGHT", "ADULT", -1L)), 0, 0L)));                      // negative cost
        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(costInput("FLIGHT", "ADULT", null)), 0, 0L)));
        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(), 100_001, 0L)));                                                  // margin > 1000 %
        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(), -1, 0L)));
        assertThrows(ValidationException.class, () -> svc.savePricing("DAD501", new PricingInput(
                List.of(), 0, -5L)));
        assertThrows(PackageNotFoundException.class, () -> svc.savePricing("NOPE", new PricingInput(List.of(), 0, 0L)));
        assertEquals(2, svc.getPricing("DAD501").costs().size());                        // failures changed nothing

        PricingView cleared = svc.savePricing("DAD501", new PricingInput(List.of(), null, null));
        assertEquals(0, cleared.costs().size());
        assertEquals(0, cleared.marginBp());
    }

    @Test
    @DisplayName("Preview: mode, margin, commission and profit per traveler type for a channel")
    public void testPreviewShowsMarginCommissionAndProfit() {
        ProductData d = priced(1200, 0, cost(1, "FLIGHT", "ADULT", 1000000));      // 12 %: sale 1,120,000
        FakeCommissions commissions = new FakeCommissions().add("AGENCY_A", null, 800, 0, true);
        AdminPricingService svc = adminService(d, commissions);

        PricingPreview p = svc.preview("DAD501", "agency_a");
        assertEquals("AGENCY_A", p.channelCode());
        assertEquals(800, p.commissionBp().intValue());
        PreviewRow adult = p.rows().stream().filter(r -> r.targetType().equals("ADULT")).findFirst().orElseThrow();
        assertEquals("COST", adult.mode());
        assertEquals(1000000L, adult.cost().longValue());
        assertEquals(120000L, adult.margin().longValue());
        assertEquals(1120000L, adult.salePrice());
        assertEquals(50000L, adult.discount());
        assertEquals(1070000L, adult.afterDiscount());
        assertEquals(1205000L, adult.payable());
        assertEquals(85600L, adult.commission());                                 // the design document example
        assertEquals(-15600L, adult.profit().longValue());                        // discount + commission exceed the margin
        PreviewRow manual = p.rows().stream().filter(r -> r.targetType().equals("INFANT")).findFirst().orElseThrow();
        assertEquals("MANUAL", manual.mode());
        assertNull(manual.cost());
        assertNull(manual.profit());

        PricingPreview direct = svc.preview("DAD501", null);
        assertEquals("DIRECT", direct.channelCode());
        assertNull(direct.commissionBp());
        assertEquals(0L, direct.rows().get(0).commission());
        PreviewRow directAdult = direct.rows().stream().filter(r -> r.targetType().equals("ADULT")).findFirst().orElseThrow();
        assertEquals(70000L, directAdult.profit().longValue());                   // 1,070,000 - 1,000,000, no commission
        assertThrows(ValidationException.class, () -> svc.preview("DAD501", "bad channel!"));
    }

    @Test
    @DisplayName("Channel commissions: validation (code, product, rates, duplicates) and whole-table replace")
    public void testChannelCommissionTableValidationAndReplace() {
        FakeCommissions commissions = new FakeCommissions().add("OLD", null, 100, 0, true);
        AdminPricingService svc = adminService(priced(0, 0), commissions);

        List<CommissionInput> saved = svc.replaceCommissions(List.of(
                new CommissionInput("agency_a", null, 800, 0L, true),
                new CommissionInput("agency_a", "dad501", 1000, null, null)));
        assertEquals(2, saved.size());
        assertEquals("AGENCY_A", saved.get(0).channelCode());
        assertEquals(1000, svc.listCommissions().get(1).commissionBp().intValue());
        assertTrue(svc.listCommissions().stream().noneMatch(r -> r.channelCode().equals("OLD")));   // replaced as a whole

        assertThrows(ValidationException.class, () -> svc.replaceCommissions(List.of(
                new CommissionInput("bad code", null, 1, 0L, true))));
        assertThrows(ValidationException.class, () -> svc.replaceCommissions(List.of(
                new CommissionInput("A", "NOPE", 1, 0L, true))));                          // unknown product
        assertThrows(ValidationException.class, () -> svc.replaceCommissions(List.of(
                new CommissionInput("A", null, 10001, 0L, true))));                        // > 100 %
        assertThrows(ValidationException.class, () -> svc.replaceCommissions(List.of(
                new CommissionInput("A", null, -1, 0L, true))));
        assertThrows(ValidationException.class, () -> svc.replaceCommissions(List.of(
                new CommissionInput("A", null, 1, -1L, true))));
        assertThrows(ValidationException.class, () -> svc.replaceCommissions(List.of(
                new CommissionInput("A", null, 1, 0L, true), new CommissionInput("a", "", 2, 0L, true))));   // duplicate
        assertEquals(2, svc.listCommissions().size());                                   // failures changed nothing
        assertEquals(0, svc.replaceCommissions(List.of()).size());                       // clearing is allowed
    }

    // ---- date-specific cost overrides ------------------------------------------------------

    private static final LocalDate PEAK = LocalDate.of(2027, 3, 10);
    private static final LocalDate NORMAL = LocalDate.of(2027, 3, 17);

    /** Adult base cost 1,000,000 (flight 520k + hotel 300k + land 180k), mark-up 25 %. */
    private ProductData baseWithCosts() {
        return priced(2500, 0, cost(1, "FLIGHT", "ADULT", 520000), cost(2, "HOTEL", "ADULT", 300000),
                cost(3, "LAND", "ADULT", 180000));
    }

    @Test
    @DisplayName("A date override replaces the cost of the same component or adds a cost line, for that date only")
    public void testCostOverridesReplaceOrAddCostLinesForOneDateOnly() {
        ProductData d = withOverride(baseWithCosts(), PEAK,
                cost(0, "HOTEL", "ADULT", 450000),               // replaces the 300,000 hotel cost
                cost(0, "OTHER", "ADULT", 50000));               // adds a peak surcharge line
        assertEquals(1000000L, d.pricing().costOf("ADULT").longValue());                 // product cost untouched
        assertEquals(1200000L, d.pricing().costOf("ADULT", PEAK).longValue());           // 520 + 450 + 180 + 50
        assertEquals(1000000L, d.pricing().costOf("ADULT", NORMAL).longValue());         // other dates: product cost

        PriceCalculator.Quote peak = calculator.quote(d, PEAK, "ADULT");
        assertEquals(1200000L, peak.cost().longValue());
        assertEquals(1500000L, peak.salePrice());                                         // 1,200,000 * 1.25
        assertEquals(1250000L, calculator.quote(d, NORMAL, "ADULT").salePrice());         // normal date unchanged
        // the discount and pass-through rules are the same on an overridden date
        assertEquals(50000L, peak.discount());
        assertEquals(135000L, peak.passThrough());
        assertEquals(1585000L, peak.payable());                                           // 1,500,000 - 50,000 + 135,000

        // overriding a component with the same amount changes nothing; another traveler type is not affected
        ProductData same = withOverride(baseWithCosts(), PEAK, cost(0, "HOTEL", "ADULT", 300000));
        assertEquals(1000000L, same.pricing().costOf("ADULT", PEAK).longValue());
        assertNull(withOverride(baseWithCosts(), PEAK, cost(0, "HOTEL", "ADULT", 1)).pricing().costOf("INFANT", PEAK));
    }

    @Test
    @DisplayName("Override rows can turn a manually priced traveler type into a cost-based one for that date only")
    public void testOverrideCanMakeAManuallyPricedTypeCostBasedForOneDate() {
        ProductData d = withOverride(baseWithCosts(), PEAK, cost(0, "FLIGHT", "NOBED_CHILD", 400000));
        assertNull(calculator.quote(d, NORMAL, "NOBED_CHILD").cost());                    // manual on other dates
        assertEquals(900000L, calculator.quote(d, NORMAL, "NOBED_CHILD").salePrice());
        PriceCalculator.Quote q = calculator.quote(d, PEAK, "NOBED_CHILD");
        assertEquals(400000L, q.cost().longValue());
        assertEquals(500000L, q.salePrice());                                             // 400,000 * 1.25
    }

    @Test
    @DisplayName("A booking snapshots the cost of its own departure date")
    public void testBookingSnapshotUsesTheCostOfItsDeparture() {
        ProductData d = withOverride(baseWithCosts(), PEAK, cost(0, "HOTEL", "ADULT", 450000));
        InMemoryPackageBookingRepository bookings = new InMemoryPackageBookingRepository();
        PackageBookingService service = bookingService(d, new FakeCommissions().add("AGENCY_A", null, 800, 0, true), bookings);

        String peak = service.create(new CreateRequest("DAD501", "2027-03-10", "AGENCY_A",
                new Contact("홍길동", "010-1234-5678", null), List.of(adult("A")))).bookingCode();
        String normal = service.create(new CreateRequest("DAD501", "2027-03-17", "AGENCY_A",
                new Contact("홍길동", "010-1234-5678", null), List.of(adult("B")))).bookingCode();
        PackageBookingData.Traveler tp = bookings.findByCode(peak).orElseThrow().travelers().get(0);
        PackageBookingData.Traveler tn = bookings.findByCode(normal).orElseThrow().travelers().get(0);
        assertEquals(1150000L, tp.unitCost().longValue());          // 520 + 450 + 180
        assertEquals(1437500L, tp.unitSalePrice());                 // 1,150,000 * 1.25
        assertEquals(1000000L, tn.unitCost().longValue());
        assertEquals(1250000L, tn.unitSalePrice());
        // commission follows the higher sale price: 8 % of (1,437,500 - 50,000) = 111,000
        assertEquals(111000L, tp.unitCommission().longValue());
        assertEquals(96000L, tn.unitCommission().longValue());
    }

    /** One store shared by the product and pricing services (the same way production shares one database). */
    private static final class Env {
        final FakeProducts store;
        final AdminPricingService pricing;
        final aerocore.backend.service.AdminProductService products;

        Env(ProductData d) {
            MutableClock clock = new MutableClock(TODAY);
            this.store = new FakeProducts().add(d);
            FakeAdmin admin = new FakeAdmin(store);
            this.pricing = new AdminPricingService(store, admin, new FakeCommissions().add("AGENCY_A", null, 800, 0, true),
                    new CostBasedPriceCalculator(), clock);
            this.products = new aerocore.backend.service.AdminProductService(store, admin, clock);
        }
    }

    @Test
    @DisplayName("Save overrides: validation, date must be a departure, the date is stored and pinned, empty list reverts")
    public void testSaveCostOverridesValidationAndStorageRule() {
        Env env = new Env(baseWithCosts());
        assertFalse(env.store.rows.containsKey("DAD501"));                               // virtual date: no row yet

        aerocore.backend.dto.AdminPricingDtos.CostOverrideView v = env.pricing.saveCostOverrides("DAD501", PEAK,
                List.of(costInput("HOTEL", "ADULT", 450000L), new CostInput("OTHER", "ADULT", 50000L, "성수기 추가")));
        assertEquals(3, v.base().size());
        assertEquals(2, v.overrides().size());
        assertEquals("성수기 추가", v.overrides().get(1).note());
        DepartureRow row = env.store.rows.get("DAD501").get(PEAK);                       // storage rule S2
        assertNotNull(row);
        assertTrue(row.pinned());
        assertEquals(10, row.capacity());                                                // default capacity snapshot
        assertEquals(2, env.pricing.getCostOverrides("DAD501", PEAK).overrides().size());
        assertEquals(0, env.pricing.getCostOverrides("DAD501", NORMAL).overrides().size());

        assertThrows(ValidationException.class, () -> env.pricing.saveCostOverrides("DAD501", PEAK,
                List.of(costInput("HOTEL", "ADULT", 1L), costInput("HOTEL", "ADULT", 2L))));        // duplicate pair
        assertThrows(ValidationException.class, () -> env.pricing.saveCostOverrides("DAD501", PEAK,
                List.of(costInput("MAGIC", "ADULT", 1L))));
        assertThrows(ValidationException.class, () -> env.pricing.saveCostOverrides("DAD501", PEAK,
                List.of(costInput("HOTEL", "SENIOR", 1L))));
        assertThrows(ValidationException.class, () -> env.pricing.saveCostOverrides("DAD501", PEAK,
                List.of(costInput("HOTEL", "ADULT", -1L))));
        assertThrows(ValidationException.class, () -> env.pricing.saveCostOverrides("DAD501", PEAK,
                List.of(costInput("HOTEL", "ADULT", null))));
        assertEquals(2, env.pricing.getCostOverrides("DAD501", PEAK).overrides().size());          // failures changed nothing

        // not a departure: a Thursday, a past date, an unknown product
        assertThrows(PackageNotFoundException.class, () -> env.pricing.saveCostOverrides("DAD501",
                LocalDate.of(2027, 3, 11), List.of(costInput("HOTEL", "ADULT", 1L))));
        assertThrows(PackageNotFoundException.class, () -> env.pricing.saveCostOverrides("DAD501",
                LocalDate.of(2026, 1, 7), List.of()));
        assertThrows(PackageNotFoundException.class, () -> env.pricing.saveCostOverrides("NOPE", PEAK, List.of()));
        assertFalse(env.store.rows.get("DAD501").containsKey(LocalDate.of(2027, 3, 11)));         // no row for rejected dates

        // an empty list reverts the date to the product's costs
        assertEquals(0, env.pricing.saveCostOverrides("DAD501", PEAK, List.of()).overrides().size());
        assertEquals(1000000L, env.store.products.get("DAD501").pricing().costOf("ADULT", PEAK).longValue());
    }

    @Test
    @DisplayName("Preview for a date marks overridden types; the departure calendar counts the overrides")
    public void testPreviewForADateAndCalendarFlags() {
        Env env = new Env(baseWithCosts());
        env.pricing.saveCostOverrides("DAD501", PEAK, List.of(costInput("HOTEL", "ADULT", 450000L)));

        PricingPreview onPeak = env.pricing.preview("DAD501", "AGENCY_A", PEAK);
        assertEquals("2027-03-10", onPeak.departureDate());
        PreviewRow adult = onPeak.rows().stream().filter(r -> r.targetType().equals("ADULT")).findFirst().orElseThrow();
        assertTrue(adult.costOverridden());
        assertEquals(1150000L, adult.cost().longValue());                                 // 520 + 450 + 180
        assertEquals(1437500L, adult.salePrice());
        assertEquals(111000L, adult.commission());
        assertEquals(1387500L - 1150000L - 111000L, adult.profit().longValue());          // 126,500
        PreviewRow infant = onPeak.rows().stream().filter(r -> r.targetType().equals("INFANT")).findFirst().orElseThrow();
        assertFalse(infant.costOverridden());

        PricingPreview onNormal = env.pricing.preview("DAD501", "AGENCY_A", NORMAL);
        assertFalse(onNormal.rows().stream().anyMatch(PreviewRow::costOverridden));
        assertEquals(1000000L, onNormal.rows().stream().filter(r -> r.targetType().equals("ADULT")).findFirst()
                .orElseThrow().cost().longValue());
        assertNull(env.pricing.preview("DAD501", "AGENCY_A").departureDate());            // no date: product costs

        List<aerocore.backend.dto.AdminProductDtos.DepartureItem> items =
                env.products.departures("DAD501", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31));
        assertEquals(1, items.stream().filter(i -> i.date().equals("2027-03-10")).findFirst().orElseThrow().costOverrides());
        assertEquals(0, items.stream().filter(i -> i.date().equals("2027-03-17")).findFirst().orElseThrow().costOverrides());
    }
}
