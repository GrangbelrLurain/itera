USE AeroCore;
GO

-- One-time cleanup of the earlier schema where every departure date was its own "package_master" row.
-- (Dev data only. Guarded by the old master table, so re-runs on the new schema do nothing.)
IF OBJECT_ID(N'dbo.package_master', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'dbo.package_booking_traveler', N'U') IS NOT NULL DROP TABLE dbo.package_booking_traveler;
    IF OBJECT_ID(N'dbo.package_booking', N'U') IS NOT NULL DROP TABLE dbo.package_booking;
    IF OBJECT_ID(N'dbo.package_price', N'U') IS NOT NULL DROP TABLE dbo.package_price;
    IF OBJECT_ID(N'dbo.package_itinerary_detail', N'U') IS NOT NULL DROP TABLE dbo.package_itinerary_detail;
    IF OBJECT_ID(N'dbo.package_optional_tour', N'U') IS NOT NULL DROP TABLE dbo.package_optional_tour;
    IF OBJECT_ID(N'dbo.package_shopping', N'U') IS NOT NULL DROP TABLE dbo.package_shopping;
    IF OBJECT_ID(N'dbo.package_remark', N'U') IS NOT NULL DROP TABLE dbo.package_remark;
    IF OBJECT_ID(N'dbo.package_condition', N'U') IS NOT NULL DROP TABLE dbo.package_condition;
    DROP TABLE dbo.package_master;
    IF OBJECT_ID(N'dbo.common_code', N'U') IS NOT NULL DROP TABLE dbo.common_code;
END
GO

-- Master product. Departure dates are NOT stored here: they are computed from departure_rule
-- (see docs/design/package-departure.md).
IF OBJECT_ID(N'dbo.product', N'U') IS NULL
CREATE TABLE dbo.product (
    product_code      NVARCHAR(20)  NOT NULL PRIMARY KEY,
    name              NVARCHAR(100) NOT NULL,
    grade             NVARCHAR(30)  NULL,
    nights            INT           NOT NULL,
    days              INT           NOT NULL,
    departure_route   NVARCHAR(20)  NULL,   -- e.g. ICN-DAD
    departure_time    NVARCHAR(5)   NULL,   -- HH:mm
    return_route      NVARCHAR(20)  NULL,
    return_time       NVARCHAR(5)   NULL,   -- arrival time of the return flight (HH:mm)
    direct_flight     BIT           NOT NULL DEFAULT 0,
    visa_required     BIT           NOT NULL DEFAULT 0,
    meeting_time      NVARCHAR(5)   NULL,
    meeting_location  NVARCHAR(100) NULL,
    hotel_name        NVARCHAR(200) NULL,
    hotel_stars       TINYINT       NULL,
    highlight         NVARCHAR(500) NULL,
    promotion_title   NVARCHAR(200) NULL,
    discount_amount   BIGINT        NOT NULL DEFAULT 0,   -- flat discount per person (not for infants)
    default_capacity  INT           NOT NULL,             -- seats given to a departure when it is first stored
    active            BIT           NOT NULL DEFAULT 1
);
GO

-- Base price per traveler type (temporary until cost-based pricing, see package-pricing.md)
IF OBJECT_ID(N'dbo.product_price', N'U') IS NULL
CREATE TABLE dbo.product_price (
    product_code      NVARCHAR(20) NOT NULL REFERENCES dbo.product(product_code),
    target_type       NVARCHAR(16) NOT NULL,   -- ADULT | NOBED_CHILD | EXTRABED_CHILD | INFANT
    base_price        BIGINT       NOT NULL,
    fuel_surcharge    BIGINT       NOT NULL DEFAULT 0,   -- pass-through
    taxes_and_charges BIGINT       NOT NULL DEFAULT 0,   -- pass-through
    sales_available   BIT          NOT NULL DEFAULT 1,
    PRIMARY KEY (product_code, target_type)
);
GO

-- Departure rule: valid period + weekdays (bit0=Mon ... bit6=Sun)
IF OBJECT_ID(N'dbo.departure_rule', N'U') IS NULL
CREATE TABLE dbo.departure_rule (
    rule_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_code NVARCHAR(20) NOT NULL REFERENCES dbo.product(product_code),
    valid_from   DATE         NOT NULL,
    valid_to     DATE         NOT NULL,
    weekdays     INT          NOT NULL CHECK (weekdays BETWEEN 1 AND 127),
    active       BIT          NOT NULL DEFAULT 1,
    CHECK (valid_to >= valid_from)
);
GO

IF OBJECT_ID(N'dbo.rule_exclusion', N'U') IS NULL
CREATE TABLE dbo.rule_exclusion (
    product_code  NVARCHAR(20) NOT NULL REFERENCES dbo.product(product_code),
    excluded_date DATE         NOT NULL,
    PRIMARY KEY (product_code, excluded_date)
);
GO

-- Stored departure: created only on first booking or an explicit admin action.
IF OBJECT_ID(N'dbo.departure', N'U') IS NULL
CREATE TABLE dbo.departure (
    product_code   NVARCHAR(20) NOT NULL REFERENCES dbo.product(product_code),
    departure_date DATE         NOT NULL,
    capacity       INT          NOT NULL,   -- snapshot of default_capacity when the row was created
    reserved_seats INT          NOT NULL DEFAULT 0,
    status         NVARCHAR(10) NOT NULL DEFAULT N'OPEN' CHECK (status IN (N'OPEN', N'CLOSED', N'CANCELED')),
    pinned         BIT          NOT NULL DEFAULT 0,   -- created/kept by an explicit admin action
    created_at     DATETIME2    NOT NULL DEFAULT SYSUTCDATETIME(),
    PRIMARY KEY (product_code, departure_date),
    CHECK (reserved_seats >= 0),
    CHECK (reserved_seats <= capacity)
);
GO

IF OBJECT_ID(N'dbo.product_itinerary', N'U') IS NULL
CREATE TABLE dbo.product_itinerary (
    product_code  NVARCHAR(20)  NOT NULL REFERENCES dbo.product(product_code),
    day_no        SMALLINT      NOT NULL,
    sort_order    SMALLINT      NOT NULL,
    service_type  NVARCHAR(12)  NOT NULL,   -- FLIGHT | SIGHTSEEING | MEAL | HOTEL | FREE
    place_name    NVARCHAR(200) NULL,
    description   NVARCHAR(4000) NULL,
    is_highlight  BIT           NOT NULL DEFAULT 0,
    is_perk       BIT           NOT NULL DEFAULT 0,
    PRIMARY KEY (product_code, day_no, sort_order)
);
GO

IF OBJECT_ID(N'dbo.product_optional_tour', N'U') IS NULL
CREATE TABLE dbo.product_optional_tour (
    product_code     NVARCHAR(20)  NOT NULL REFERENCES dbo.product(product_code),
    seq              SMALLINT      NOT NULL,
    name             NVARCHAR(200) NOT NULL,
    place_name       NVARCHAR(200) NULL,
    name_en          NVARCHAR(200) NULL,
    summary          NVARCHAR(500) NULL,
    details          NVARCHAR(MAX) NULL,
    currency         NVARCHAR(3)   NULL,
    price_adult      DECIMAL(12,2) NULL,
    price_child      DECIMAL(12,2) NULL,
    min_participants SMALLINT      NULL,
    duration_minutes INT           NULL,
    included         BIT           NULL,
    pre_sale         BIT           NULL,
    PRIMARY KEY (product_code, seq)
);
GO

IF OBJECT_ID(N'dbo.product_shopping', N'U') IS NULL
CREATE TABLE dbo.product_shopping (
    product_code     NVARCHAR(20)  NOT NULL REFERENCES dbo.product(product_code),
    seq              SMALLINT      NOT NULL,
    item_name        NVARCHAR(200) NOT NULL,
    place_name       NVARCHAR(200) NULL,
    duration_minutes INT           NULL,
    refundable       BIT           NOT NULL DEFAULT 0,
    PRIMARY KEY (product_code, seq)
);
GO

IF OBJECT_ID(N'dbo.product_remark', N'U') IS NULL
CREATE TABLE dbo.product_remark (
    product_code    NVARCHAR(20) NOT NULL PRIMARY KEY REFERENCES dbo.product(product_code),
    price_note      NVARCHAR(MAX) NULL,
    inclusions      NVARCHAR(MAX) NULL,
    exclusions      NVARCHAR(MAX) NULL,
    highlights      NVARCHAR(MAX) NULL,
    booking_notice  NVARCHAR(MAX) NULL,
    room_info       NVARCHAR(MAX) NULL,
    travel_notice   NVARCHAR(MAX) NULL,
    faq             NVARCHAR(MAX) NULL
);
GO

IF OBJECT_ID(N'dbo.product_condition', N'U') IS NULL
CREATE TABLE dbo.product_condition (
    product_code             NVARCHAR(20) NOT NULL PRIMARY KEY REFERENCES dbo.product(product_code),
    airline_seat_class       NVARCHAR(30)  NULL,
    tour_leader              BIT           NOT NULL DEFAULT 0,
    local_guide              NVARCHAR(50)  NULL,
    insurance_included       BIT           NOT NULL DEFAULT 0,
    shopping_count           INT           NOT NULL DEFAULT 0,
    optional_tour_included   BIT           NULL,
    admission_fee_included   BIT           NOT NULL DEFAULT 0,
    local_expenses_currency  NVARCHAR(3)   NULL,
    local_expenses_adult     INT           NOT NULL DEFAULT 0,
    local_expenses_child     INT           NOT NULL DEFAULT 0,
    local_expenses_infant    INT           NOT NULL DEFAULT 0,
    single_room_fee          INT           NOT NULL DEFAULT 0,
    min_departure_population INT           NOT NULL DEFAULT 0,
    cancellation_policy      NVARCHAR(MAX) NULL
);
GO
