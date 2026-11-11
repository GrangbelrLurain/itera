USE AeroCore;
GO

-- A booking points at a stored departure row (product_code, departure_date). That row is created
-- in the same transaction as the first booking for the date (see docs/design/package-departure.md).
IF OBJECT_ID(N'dbo.package_booking', N'U') IS NULL
CREATE TABLE dbo.package_booking (
    booking_code      NVARCHAR(16)  NOT NULL PRIMARY KEY,
    product_code      NVARCHAR(20)  NOT NULL,
    departure_date    DATE          NOT NULL,
    status            NVARCHAR(16)  NOT NULL,  -- PENDING_PAYMENT | CONFIRMED | CANCELLED | EXPIRED
    channel_code      NVARCHAR(30)  NOT NULL DEFAULT N'DIRECT',
    package_name      NVARCHAR(200) NOT NULL,  -- snapshot at booking time
    contact_name      NVARCHAR(100) NOT NULL,
    contact_phone     NVARCHAR(30)  NOT NULL,
    contact_email     NVARCHAR(200) NULL,
    seat_count        INT           NOT NULL,
    total_amount      BIGINT        NOT NULL,
    refund_amount     BIGINT        NOT NULL DEFAULT 0,
    penalty_amount    BIGINT        NOT NULL DEFAULT 0,
    payment_reference NVARCHAR(100) NULL,
    cancel_reason     NVARCHAR(500) NULL,
    created_at        DATETIME2     NOT NULL,
    payment_due_at    DATETIME2     NOT NULL,
    confirmed_at      DATETIME2     NULL,
    cancelled_at      DATETIME2     NULL,
    FOREIGN KEY (product_code, departure_date) REFERENCES dbo.departure(product_code, departure_date)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_package_booking_departure' AND object_id = OBJECT_ID(N'dbo.package_booking'))
CREATE INDEX ix_package_booking_departure ON dbo.package_booking(product_code, departure_date, status);
GO

-- Price snapshot per traveler (package-pricing.md section 4). Cost/margin/commission stay NULL until
-- cost-based pricing exists.
IF OBJECT_ID(N'dbo.package_booking_traveler', N'U') IS NULL
CREATE TABLE dbo.package_booking_traveler (
    booking_code     NVARCHAR(16)  NOT NULL REFERENCES dbo.package_booking(booking_code),
    seq              SMALLINT      NOT NULL,
    target_type      NVARCHAR(16)  NOT NULL,  -- ADULT | NOBED_CHILD | EXTRABED_CHILD | INFANT
    full_name        NVARCHAR(100) NOT NULL,
    birth_date       DATE          NOT NULL,
    gender           NCHAR(1)      NOT NULL,  -- M | F
    unit_price       BIGINT        NOT NULL,  -- what the customer pays (sale price after discount + pass-through)
    unit_sale_price  BIGINT        NOT NULL,  -- sale price before discount
    unit_discount    BIGINT        NOT NULL DEFAULT 0,
    unit_pass_through BIGINT       NOT NULL DEFAULT 0,  -- fuel surcharge + taxes
    unit_cost        BIGINT        NULL,
    unit_commission  BIGINT        NULL,
    PRIMARY KEY (booking_code, seq)
);
GO
