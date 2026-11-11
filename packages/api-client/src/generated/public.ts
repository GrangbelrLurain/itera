// Generated from contracts/public.openapi.json by scripts/generate.mjs. Do not edit.
export interface paths {
    "/api/v1/bookings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Create a booking and lock a seat */
        post: operations["createBooking"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/bookings/{bookingId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Booking by id */
        get: operations["getBooking"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/bookings/{bookingId}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a booking */
        post: operations["cancelBooking"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/health": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Liveness check */
        get: operations["getHealth"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/package-bookings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Book seats of a departure (pending payment) */
        post: operations["createPackageBooking"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/package-bookings/{bookingCode}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Booking by code */
        get: operations["getPackageBooking"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/package-bookings/{bookingCode}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a booking (penalty by days before departure) */
        post: operations["cancelPackageBooking"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/package-bookings/{bookingCode}/pay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Confirm the payment of a pending booking */
        post: operations["payPackageBooking"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/packages": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Departures of the packages in a date range */
        get: operations["listPackages"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/packages/{productCode}/departures/{departureDate}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Package detail of one departure date */
        get: operations["getPackage"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/tickets/issue": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Issue the e-ticket of a paid booking */
        post: operations["issueTicket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/tickets/{ticketNumber}/download.pdf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** E-ticket PDF */
        get: operations["downloadTicketPdf_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/tickets/{ticketNumber}/pdf": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** E-ticket PDF */
        get: operations["downloadTicketPdf"];
        put?: never;
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
        BookingDetailResponse: {
            bookingId?: string;
            departureDate?: string;
            flightNumber?: string;
            passengerName?: string;
            passengerPassportNumber?: string;
            /** Format: int64 */
            paymentDueTimestamp?: number;
            /** Format: int64 */
            penaltyFee?: number;
            pnrCode?: string;
            /** Format: int64 */
            refundAmount?: number;
            seatNumber?: string;
            status?: string;
            ticketNumber?: string;
            /** Format: int64 */
            totalAmount?: number;
        };
        CancelBookingRequest: {
            cancelReason?: string;
        };
        CancelBookingResponse: {
            bookingId?: string;
            /** Format: int64 */
            penaltyFee?: number;
            /** Format: int64 */
            refundAmount?: number;
            status?: string;
        };
        CancelRequest: {
            reason?: string;
        };
        Contact: {
            email: string | null;
            name: string;
            phone: string;
        };
        CreateBookingRequest: {
            departureDate?: string;
            flightNumber?: string;
            passengerName?: string;
            passengerPassportNumber?: string;
            /** Format: int64 */
            totalAmount?: number;
        };
        CreateBookingResponse: {
            bookingId?: string;
            /** Format: int64 */
            paymentDueTimestamp?: number;
            pnrCode?: string;
            status?: string;
        };
        CreatePackageBookingRequest: {
            channelCode?: string;
            contact: components["schemas"]["Contact"];
            /** Format: date */
            departureDate: string;
            productCode: string;
            travelers: components["schemas"]["TravelerInput"][];
        };
        ErrorResponse: {
            code: string;
            error: string;
        };
        HealthResponse: {
            status?: string;
            version?: string;
        };
        IssueTicketRequest: {
            bookingId?: string;
            paymentTransactionId?: string;
        };
        IssueTicketResponse: {
            base64Data?: string;
            bookingId?: string;
            pdfDownloadUrl?: string;
            status?: string;
            ticketNumber?: string;
        };
        ItineraryDay: {
            /** Format: date */
            date: string;
            /** Format: int32 */
            day: number;
            items: components["schemas"]["ItineraryItem"][];
        };
        ItineraryItem: {
            description: string | null;
            isHighlight: boolean;
            isPerk: boolean;
            placeName: string | null;
            /** @enum {string} */
            serviceType: "FLIGHT" | "SIGHTSEEING" | "MEAL" | "HOTEL" | "FREE";
        };
        PackageBasic: {
            arrivalInfo: string;
            /** Format: int32 */
            capacity: number;
            /** Format: int32 */
            days: number;
            /** Format: date */
            departureDate: string;
            departureInfo: string;
            /** Format: date */
            freeCancellationUntil: string;
            grade: string | null;
            highlight: string | null;
            hotelName: string | null;
            /** Format: int32 */
            hotelStars: number | null;
            isDirectFlight: boolean;
            isStoredDeparture: boolean;
            isVisaRequired: boolean;
            meetingLocation: string | null;
            meetingTime: string | null;
            name: string;
            /** Format: int32 */
            nights: number;
            productCode: string;
            promotionTitle: string | null;
            /** Format: int32 */
            remainingSeats: number;
            /** Format: int32 */
            reservedSeats: number;
            /** @enum {string} */
            status: "OPEN" | "FULL" | "CLOSED";
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
        PackageCondition: {
            airlineSeatClass: string | null;
            cancellationPolicy: string | null;
            hasTourLeader: boolean;
            isAdmissionFeeIncluded: boolean;
            isInsuranceIncluded: boolean;
            isOptionalTourIncluded: boolean | null;
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
            /** Format: int32 */
            shoppingCount: number;
            /** Format: int32 */
            singleRoomFee: number;
        };
        PackageDetail: {
            basic: components["schemas"]["PackageBasic"];
            condition: components["schemas"]["PackageCondition"] | null;
            itinerary: components["schemas"]["PackageItinerary"];
            optionalTours: components["schemas"]["PackageOptionalTour"][];
            prices: components["schemas"]["PackagePrice"][];
            remark: components["schemas"]["PackageRemark"] | null;
            shoppings: components["schemas"]["PackageShopping"][];
        };
        PackageItinerary: {
            days: components["schemas"]["ItineraryDay"][];
        };
        PackageOptionalTour: {
            currency: string | null;
            details: string | null;
            /** Format: int32 */
            durationMinutes: number | null;
            isIncluded: boolean | null;
            isPreSale: boolean | null;
            /** Format: int32 */
            minParticipants: number | null;
            name: string;
            nameEn: string | null;
            placeName: string | null;
            /** Format: double */
            priceAdult: number | null;
            /** Format: double */
            priceChild: number | null;
            /** Format: int32 */
            seq: number;
            summary: string | null;
        };
        PackagePrice: {
            /** Format: int64 */
            discount: number;
            /** Format: int64 */
            discountedTotalPrice: number;
            /** Format: int64 */
            fuelSurcharge: number;
            isSalesAvailable: boolean;
            /** Format: int64 */
            salePrice: number;
            /** @enum {string} */
            targetType: "ADULT" | "NOBED_CHILD" | "EXTRABED_CHILD" | "INFANT";
            /** Format: int64 */
            taxesAndCharges: number;
            /** Format: int64 */
            totalPrice: number;
        };
        PackageRemark: {
            bookingNotice: string | null;
            exclusions: string | null;
            faq: string | null;
            highlights: string | null;
            inclusions: string | null;
            priceNote: string | null;
            roomInfo: string | null;
            travelNotice: string | null;
        };
        PackageShopping: {
            /** Format: int32 */
            durationMinutes: number | null;
            isRefundable: boolean;
            itemName: string;
            placeName: string | null;
            /** Format: int32 */
            seq: number;
        };
        PackageSummary: {
            /** Format: int64 */
            adultDiscountedTotalPrice: number;
            /** Format: int64 */
            adultTotalPrice: number;
            /** Format: int32 */
            days: number;
            /** Format: date */
            departureDate: string;
            name: string;
            /** Format: int32 */
            nights: number;
            productCode: string;
            /** Format: int32 */
            remainingSeats: number;
            /** @enum {string} */
            status: "OPEN" | "FULL" | "CLOSED";
        };
        PackageSummaryPage: {
            items: components["schemas"]["PackageSummary"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            total: number;
        };
        PaymentRequest: {
            paymentReference?: string;
        };
        TravelerInput: {
            /** Format: date */
            birthDate: string;
            /** @enum {string} */
            gender: "M" | "F";
            name: string;
            /** @enum {string} */
            targetType: "ADULT" | "NOBED_CHILD" | "EXTRABED_CHILD" | "INFANT";
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
    createBooking: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateBookingRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CreateBookingResponse"];
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
    getBooking: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                bookingId: string;
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
                    "application/json": components["schemas"]["BookingDetailResponse"];
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
    cancelBooking: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                bookingId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["CancelBookingRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["CancelBookingResponse"];
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
    getHealth: {
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
                    "application/json": components["schemas"]["HealthResponse"];
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
    createPackageBooking: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreatePackageBookingRequest"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["PackageBooking"];
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
    getPackageBooking: {
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
                    "application/json": components["schemas"]["PackageBooking"];
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
    cancelPackageBooking: {
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
    payPackageBooking: {
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
    listPackages: {
        parameters: {
            query?: {
                from?: string;
                to?: string;
                keyword?: string;
                /** @description open, full, closed (comma separated) */
                status?: string;
                /** @description MON,TUE,... (comma separated) */
                weekdays?: string;
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
                    "application/json": components["schemas"]["PackageSummaryPage"];
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
    getPackage: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                productCode: string;
                departureDate: string;
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
                    "application/json": components["schemas"]["PackageDetail"];
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
    issueTicket: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["IssueTicketRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["IssueTicketResponse"];
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
    downloadTicketPdf_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                ticketNumber: string;
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
                    "application/json": string;
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
    downloadTicketPdf: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                ticketNumber: string;
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
                    "application/json": string;
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
