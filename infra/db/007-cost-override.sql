USE AeroCore;
GO

-- Date-specific cost adjustments (docs/design/package-pricing.md). For a departure, the product's cost rows apply
-- except where a row here has the same (component_type, target_type): that cost replaces the product's; a row
-- whose component the product does not have simply adds a cost line (e.g. a peak-season surcharge).
-- Saving overrides is an admin action on the date, so the departure row exists (and is pinned) - hence the FK.
IF OBJECT_ID(N'dbo.departure_cost_override', N'U') IS NULL
CREATE TABLE dbo.departure_cost_override (
    product_code   NVARCHAR(20)  NOT NULL,
    departure_date DATE          NOT NULL,
    component_type NVARCHAR(12)  NOT NULL,   -- FLIGHT | HOTEL | LAND | TOUR | INSURANCE | OTHER
    target_type    NVARCHAR(16)  NOT NULL,   -- ADULT | NOBED_CHILD | EXTRABED_CHILD | INFANT
    cost           BIGINT        NOT NULL CHECK (cost >= 0),
    note           NVARCHAR(100) NULL,
    PRIMARY KEY (product_code, departure_date, component_type, target_type),
    FOREIGN KEY (product_code, departure_date) REFERENCES dbo.departure(product_code, departure_date)
);
GO
