// Generated from contracts/admin.openapi.json by scripts/generate.mjs. Do not edit.
export interface paths {
    "/api/v1/admin/bookings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Search bookings */
        get: operations["listAdminBookings"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/bookings/{bookingCode}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Booking with cost, commission and profit per traveler */
        get: operations["getAdminBooking"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/bookings/{bookingCode}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a booking (penalty by days before departure) */
        post: operations["adminCancelBooking"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/bookings/{bookingCode}/pay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Confirm the payment of a pending booking */
        post: operations["adminConfirmPayment"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/channel-commissions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Commission rules of the sales channels */
        get: operations["listAdminChannelCommissions"];
        /** Replace the whole commission table */
        put: operations["replaceAdminChannelCommissions"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/dashboard": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Booking and seat summary */
        get: operations["getAdminDashboard"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Sign in and get a bearer token */
        post: operations["adminLogin"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** All master products */
        get: operations["listAdminProducts"];
        put?: never;
        /** Create a master product */
        post: operations["createAdminProduct"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Product with schedule, itinerary and extras */
        get: operations["getAdminProduct"];
        /** Update the basic information and prices */
        put: operations["updateAdminProduct"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/active": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Enable or disable sales of a product */
        post: operations["setAdminProductActive"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/departures": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Departure dates of a range (rule dates plus stored rows) */
        get: operations["listAdminDepartures"];
        put?: never;
        /** Add a single departure date outside the rules */
        post: operations["addAdminDeparture"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/departures/{date}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a departure (only without bookings) */
        post: operations["cancelAdminDeparture"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/departures/{date}/capacity": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Change the seat capacity of a departure */
        put: operations["setAdminDepartureCapacity"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/departures/{date}/close": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Stop new bookings of a departure */
        post: operations["closeAdminDeparture"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/departures/{date}/cost-overrides": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** The product's cost rows and the cost overrides of one departure date */
        get: operations["getAdminCostOverrides"];
        /** Replace the cost overrides of a date; an empty list reverts the date to the product costs */
        put: operations["saveAdminCostOverrides"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/departures/{date}/reopen": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Reopen a closed or canceled departure */
        post: operations["reopenAdminDeparture"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/extras": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Replace optional tours, shopping, remarks and conditions */
        put: operations["replaceAdminExtras"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/itinerary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Replace the itinerary */
        put: operations["replaceAdminItinerary"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/pricing": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Cost components and mark-up of a product */
        get: operations["getAdminPricing"];
        /** Replace the cost components and mark-up */
        put: operations["saveAdminPricing"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/pricing/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Sale price, commission and profit per traveler type for a channel (and optionally a date) */
        get: operations["previewAdminPricing"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{productCode}/schedule": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Replace the departure rules and excluded dates */
        put: operations["replaceAdminSchedule"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ActiveRequest: {
            active: boolean;
        };
        AdminBookingDetail: {
            booking: components["schemas"]["PackageBooking"];
            finance: components["schemas"]["TravelerFinance"][];
            /** Format: int64 */
            profit: number | null;
            /** Format: int64 */
            totalCommission: number;
            /** Format: int64 */
            totalCost: number | null;
        };
        AdminBookingPage: {
            items: components["schemas"]["BookingSummary"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            total: number;
        };
        BookingSummary: {
            bookingCode: string;
            channelCode: string;
            contactName: string;
            contactPhone: string;
            /** Format: date-time */
            createdAt: string;
            /** Format: date */
            departureDate: string;
            packageName: string;
            /** Format: date-time */
            paymentDueAt: string;
            productCode: string;
            /** Format: int32 */
            seatCount: number;
            /** @enum {string} */
            status: "PENDING_PAYMENT" | "CONFIRMED" | "CANCELLED" | "EXPIRED";
            /** Format: int64 */
            totalAmount: number;
        };
        CancelRequest: {
            reason?: string;
        };
        CapacityRequest: {
            /** Format: int32 */
            capacity: number;
        };
        CommissionInput: {
            active: boolean;
            channelCode: string;
            /** Format: int32 */
            commissionBp: number;
            /** Format: int64 */
            commissionFixed: number;
            productCode: string | null;
        };
        CommissionRowsRequest: {
            rows: components["schemas"]["CommissionInput"][];
        };
        ConditionInput: {
            admissionFeeIncluded: boolean;
            airlineSeatClass: string | null;
            cancellationPolicy: string | null;
            insuranceIncluded: boolean;
            /** Format: int32 */
            localExpensesAdult: number;
            /** Format: int32 */
            localExpensesChild: number;
            localExpensesCurrency: string | null;
            /** Format: int32 */
            localExpensesInfant: number;
            localGuide: string | null;
            /** Format: int32 */
            minDeparturePopulation: number;
            optionalTourIncluded: boolean | null;
            /** Format: int32 */
            shoppingCount: number;
            /** Format: int32 */
            singleRoomFee: number;
            tourLeader: boolean;
        };
        Contact: {
            email: string | null;
            name: string;
            phone: string;
        };
        CostInput: {
            /** @enum {string} */
            componentType: "FLIGHT" | "HOTEL" | "LAND" | "TOUR" | "INSURANCE" | "OTHER";
            /** Format: int64 */
            cost: number;
            note: string | null;
            /** @enum {string} */
            targetType: "ADULT" | "NOBED_CHILD" | "EXTRABED_CHILD" | "INFANT";
        };
        CostOverrideView: {
            base: components["schemas"]["CostInput"][];
            /** Format: date */
            departureDate: string;
            overrides: components["schemas"]["CostInput"][];
        };
        CostRowsRequest: {
            rows: components["schemas"]["CostInput"][];
        };
        Dashboard: {
            /** Format: int64 */
            confirmedRevenue: number;
            lowSeatDepartures: components["schemas"]["LowSeatDeparture"][];
            /** Format: int64 */
            pendingExpiringWithin24h: number;
            statusCounts: {
                [key: string]: number;
            };
        };
        DepartureDateRequest: {
            /** Format: date */
            date: string;
        };
        DepartureItem: {
            /** Format: int32 */
            capacity: number;
            /** Format: int32 */
            costOverrides: number;
            /** Format: date */
            date: string;
            fromRule: boolean;
            pinned: boolean;
            /** Format: int32 */
            remainingSeats: number;
            /** Format: int32 */
            reservedSeats: number;
            /** @enum {string} */
            status: "OPEN" | "FULL" | "CLOSED" | "CANCELED";
            stored: boolean;
            /** @enum {string} */
            weekday: "MON" | "TUE" | "WED" | "THU" | "FRI" | "SAT" | "SUN";
        };
        ErrorResponse: {
            code: string;
            error: string;
        };
        ExtrasInput: {
            condition: components["schemas"]["ConditionInput"] | null;
            optionalTours: components["schemas"]["OptionalTourInput"][];
            remark: components["schemas"]["RemarkInput"] | null;
            shoppings: components["schemas"]["ShoppingInput"][];
        };
        ItineraryInput: {
            /** Format: int32 */
            dayNo: number;
            description: string | null;
            highlight: boolean;
            perk: boolean;
            placeName: string | null;
            /** @enum {string} */
            serviceType: "FLIGHT" | "SIGHTSEEING" | "MEAL" | "HOTEL" | "FREE";
        };
        ItineraryRowsRequest: {
            rows: components["schemas"]["ItineraryInput"][];
        };
        LoginRequest: {
            password: string;
            username: string;
        };
        LoginResponse: {
            /** Format: date-time */
            expiresAt: string;
            token: string;
        };
        LowSeatDeparture: {
            /** Format: date */
            departureDate: string;
            name: string;
            productCode: string;
            /** Format: int32 */
            remainingSeats: number;
        };
        OptionalTourInput: {
            currency: string | null;
            details: string | null;
            /** Format: int32 */
            durationMinutes: number | null;
            included: boolean | null;
            /** Format: int32 */
            minParticipants: number | null;
            name: string;
            nameEn: string | null;
            placeName: string | null;
            preSale: boolean | null;
            /** Format: double */
            priceAdult: number | null;
            /** Format: double */
            priceChild: number | null;
            summary: string | null;
        };
        PackageBooking: {
            bookingCode: string;
            cancelReason: string | null;
            /** Format: date-time */
            cancelledAt: string | null;
            channelCode: string;
            /** Format: date-time */
            confirmedAt: string | null;
            contact: components["schemas"]["Contact"];
            /** Format: date-time */
            createdAt: string;
            /** Format: date */
            departureDate: string;
            packageName: string;
            /** Format: date-time */
            paymentDueAt: string;
            paymentReference: string | null;
            /** Format: int64 */
            penaltyAmount: number;
            productCode: string;
            /** Format: int64 */
            refundAmount: number;
            /** Format: int32 */
            seatCount: number;
            /** @enum {string} */
            status: "PENDING_PAYMENT" | "CONFIRMED" | "CANCELLED" | "EXPIRED";
            /** Format: int64 */
            totalAmount: number;
            travelers: components["schemas"]["PackageBookingTraveler"][];
        };
        PackageBookingTraveler: {
            /** Format: date */
            birthDate: string;
            /** @enum {string} */
            gender: "M" | "F";
            name: string;
            /** Format: int32 */
            seq: number;
            /** @enum {string} */
            targetType: "ADULT" | "NOBED_CHILD" | "EXTRABED_CHILD" | "INFANT";
            /** Format: int64 */
            unitPrice: number;
        };
        PaymentRequest: {
            paymentReference?: string;
        };
        PreviewRow: {
            /** Format: int64 */
            afterDiscount: number;
            /** Format: int64 */
            commission: number;
            /** Format: int64 */
            cost: number | null;
            costOverridden: boolean;
            /** Format: int64 */
            discount: number;
            /** Format: int64 */
            margin: number | null;
            /** @enum {string} */
            mode: "COST" | "MANUAL";
            /** Format: int64 */
            passThrough: number;
            /** Format: int64 */
            payable: number;
            /** Format: int64 */
            profit: number | null;
            /** Format: int64 */
            salePrice: number;
            salesAvailable: boolean;
            /** @enum {string} */
            targetType: "ADULT" | "NOBED_CHILD" | "EXTRABED_CHILD" | "INFANT";
        };
        PriceInput: {
            /** Format: int64 */
            basePrice: number;
            /** Format: int64 */
            fuelSurcharge: number;
            salesAvailable: boolean;
            /** @enum {string} */
            targetType: "ADULT" | "NOBED_CHILD" | "EXTRABED_CHILD" | "INFANT";
            /** Format: int64 */
            taxesAndCharges: number;
        };
        PricingInput: {
            costs: components["schemas"]["CostInput"][];
            /** Format: int32 */
            marginBp: number;
            /** Format: int64 */
            marginFixed: number;
        };
        PricingPreview: {
            channelCode: string;
            /** Format: int32 */
            commissionBp: number | null;
            /** Format: int64 */
            commissionFixed: number | null;
            /** Format: date */
            departureDate: string | null;
            rows: components["schemas"]["PreviewRow"][];
        };
        PricingView: {
            costs: components["schemas"]["CostInput"][];
            /** Format: int32 */
            marginBp: number;
            /** Format: int64 */
            marginFixed: number;
        };
        ProductDetail: {
            extras: components["schemas"]["ExtrasInput"];
            itinerary: components["schemas"]["ItineraryInput"][];
            product: components["schemas"]["ProductInput"];
            schedule: components["schemas"]["ScheduleInput"];
        };
        ProductInput: {
            active: boolean;
            code: string;
            /** Format: int32 */
            days: number;
            /** Format: int32 */
            defaultCapacity: number;
            departureRoute: string | null;
            departureTime: string | null;
            directFlight: boolean;
            /** Format: int64 */
            discountAmount: number;
            grade: string | null;
            highlight: string | null;
            hotelName: string | null;
            /** Format: int32 */
            hotelStars: number | null;
            meetingLocation: string | null;
            meetingTime: string | null;
            name: string;
            /** Format: int32 */
            nights: number;
            prices: components["schemas"]["PriceInput"][];
            promotionTitle: string | null;
            returnRoute: string | null;
            returnTime: string | null;
            visaRequired: boolean;
        };
        ProductSummary: {
            active: boolean;
            code: string;
            /** Format: int32 */
            days: number;
            /** Format: int32 */
            defaultCapacity: number;
            grade: string;
            name: string;
            /** Format: int32 */
            nights: number;
            scheduleSummary: string;
        };
        RemarkInput: {
            bookingNotice: string | null;
            exclusions: string | null;
            faq: string | null;
            highlights: string | null;
            inclusions: string | null;
            priceNote: string | null;
            roomInfo: string | null;
            travelNotice: string | null;
        };
        RuleInput: {
            active: boolean;
            validFrom: string;
            validTo: string;
            weekdays: string[];
        };
        ScheduleInput: {
            exclusions: string[];
            rules: components["schemas"]["RuleInput"][];
        };
        ShoppingInput: {
            /** Format: int32 */
            durationMinutes: number | null;
            itemName: string;
            placeName: string | null;
            refundable: boolean;
        };
        TravelerFinance: {
            /** Format: int32 */
            seq: number;
            /** Format: int64 */
            unitCommission: number | null;
            /** Format: int64 */
            unitCost: number | null;
            /** Format: int64 */
            unitDiscount: number;
            /** Format: int64 */
            unitPassThrough: number;
            /** Format: int64 */
            unitSalePrice: number;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    listAdminBookings: {
        parameters: {
            query?: {
                /** @description PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED (comma separated) */
                status?: string;
                from?: string;
                to?: string;
                productCode?: string;
                departureDate?: string;
                keyword?: string;
                page?: number;
                size?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminBookingPage"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAdminBooking: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                bookingCode: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AdminBookingDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    adminCancelBooking: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                bookingCode: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["CancelRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PackageBooking"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    adminConfirmPayment: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                bookingCode: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["PaymentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PackageBooking"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listAdminChannelCommissions: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CommissionInput"][];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    replaceAdminChannelCommissions: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CommissionRowsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CommissionInput"][];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAdminDashboard: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Dashboard"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    adminLogin: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LoginRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["LoginResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listAdminProducts: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductSummary"][];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    createAdminProduct: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProductInput"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAdminProduct: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    updateAdminProduct: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProductInput"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    setAdminProductActive: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ActiveRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    listAdminDepartures: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
            };
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartureItem"][];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    addAdminDeparture: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DepartureDateRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartureItem"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    cancelAdminDeparture: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                date: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartureItem"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    setAdminDepartureCapacity: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                date: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CapacityRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartureItem"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    closeAdminDeparture: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                date: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartureItem"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAdminCostOverrides: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                date: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CostOverrideView"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    saveAdminCostOverrides: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                date: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CostRowsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CostOverrideView"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    reopenAdminDeparture: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                date: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["DepartureItem"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    replaceAdminExtras: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ExtrasInput"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    replaceAdminItinerary: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ItineraryRowsRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    getAdminPricing: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PricingView"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    saveAdminPricing: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["PricingInput"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PricingView"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    previewAdminPricing: {
        parameters: {
            query?: {
                /** @description Sales channel code (default DIRECT) */
                channel?: string;
                /** @description Departure date whose cost overrides are applied (default: product costs only) */
                date?: string;
            };
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PricingPreview"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
    replaceAdminSchedule: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ScheduleInput"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProductDetail"];
                };
            };
            /** @description Missing, expired or invalid bearer token (code UNAUTHENTICATED) */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
            /** @description Error (see the code field) */
            default: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorResponse"];
                };
            };
        };
    };
}
