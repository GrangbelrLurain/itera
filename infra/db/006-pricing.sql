USE AeroCore;
GO

-- Cost components per product and traveler type (docs/design/package-pricing.md).
-- When a traveler type has cost rows, its sale price is computed from them; otherwise product_price.base_price is used.
IF OBJECT_ID(N'dbo.product_cost_component', N'U') IS NULL
CREATE TABLE dbo.product_cost_component (
    product_code   NVARCHAR(20)  NOT NULL REFERENCES dbo.product(product_code),
    seq            SMALLINT      NOT NULL,
    component_type NVARCHAR(12)  NOT NULL,   -- FLIGHT | HOTEL | LAND | TOUR | INSURANCE | OTHER
    target_type    NVARCHAR(16)  NOT NULL,   -- ADULT | NOBED_CHILD | EXTRABED_CHILD | INFANT
    cost           BIGINT        NOT NULL CHECK (cost >= 0),
    note           NVARCHAR(100) NULL,
    PRIMARY KEY (product_code, seq)
);
GO

-- Markup policy (rates in bp = 1/100 %): marked up = cost + cost * margin_bp / 10000 + margin_fixed
IF OBJECT_ID(N'dbo.product_pricing_policy', N'U') IS NULL
CREATE TABLE dbo.product_pricing_policy (
    product_code NVARCHAR(20) NOT NULL PRIMARY KEY REFERENCES dbo.product(product_code),
    margin_bp    INT          NOT NULL DEFAULT 0 CHECK (margin_bp BETWEEN 0 AND 100000),
    margin_fixed BIGINT       NOT NULL DEFAULT 0 CHECK (margin_fixed >= 0)
);
GO

-- Sales-channel commission. product_code '*' = every product; a product row overrides the channel's '*' row.
IF OBJECT_ID(N'dbo.channel_commission', N'U') IS NULL
CREATE TABLE dbo.channel_commission (
    channel_code     NVARCHAR(30) NOT NULL,
    product_code     NVARCHAR(20) NOT NULL DEFAULT N'*',
    commission_bp    INT          NOT NULL DEFAULT 0 CHECK (commission_bp BETWEEN 0 AND 10000),
    commission_fixed BIGINT       NOT NULL DEFAULT 0 CHECK (commission_fixed >= 0),
    active           BIT          NOT NULL DEFAULT 1,
    PRIMARY KEY (channel_code, product_code)
);
GO

-- SAMPLE pricing for the main sample product: adult and no-bed child are cost based, infant stays a manual price.
IF NOT EXISTS (SELECT 1 FROM dbo.product_cost_component WHERE product_code = N'DAD501')
BEGIN
    INSERT dbo.product_cost_component (product_code, seq, component_type, target_type, cost, note) VALUES
        (N'DAD501', 1, N'FLIGHT', N'ADULT',       520000, N'왕복 항공'),
        (N'DAD501', 2, N'HOTEL',  N'ADULT',       300000, N'4박 호텔'),
        (N'DAD501', 3, N'LAND',   N'ADULT',       150000, N'차량·가이드'),
        (N'DAD501', 4, N'TOUR',   N'ADULT',        60000, N'입장료'),
        (N'DAD501', 5, N'FLIGHT', N'NOBED_CHILD', 520000, N'왕복 항공'),
        (N'DAD501', 6, N'HOTEL',  N'NOBED_CHILD', 200000, N'노베드'),
        (N'DAD501', 7, N'LAND',   N'NOBED_CHILD', 150000, N'차량·가이드'),
        (N'DAD501', 8, N'TOUR',   N'NOBED_CHILD',  60000, N'입장료');
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.product_pricing_policy WHERE product_code = N'DAD501')
    INSERT dbo.product_pricing_policy (product_code, margin_bp, margin_fixed) VALUES (N'DAD501', 2500, 0);   -- 25%
GO

IF NOT EXISTS (SELECT 1 FROM dbo.channel_commission)
    INSERT dbo.channel_commission (channel_code, product_code, commission_bp, commission_fixed, active) VALUES
        (N'AGENCY_A', N'*',      800, 0, 1),    -- 8% of the sale price after discount
        (N'AGENCY_B', N'*',      500, 0, 1),    -- 5% ...
        (N'AGENCY_B', N'DAD501', 1000, 0, 1),   -- ... but 10% on this product
        (N'PARTNER_C', N'*',       0, 20000, 1); -- fixed 20,000 per person
GO
