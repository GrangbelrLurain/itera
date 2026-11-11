import type { AdminSchemas, PublicSchemas } from '@itera/api-client';

/** Shapes of the generated contract that the presentation domain works with (types only, no runtime import). */
export type ProductInput = AdminSchemas['ProductInput'];
export type PriceInput = AdminSchemas['PriceInput'];
export type ItineraryInput = AdminSchemas['ItineraryInput'];
export type RuleInput = AdminSchemas['RuleInput'];
export type ScheduleInput = AdminSchemas['ScheduleInput'];
export type OptionalTourInput = AdminSchemas['OptionalTourInput'];
export type ShoppingInput = AdminSchemas['ShoppingInput'];
export type RemarkInput = AdminSchemas['RemarkInput'];
export type ConditionInput = AdminSchemas['ConditionInput'];
export type ExtrasInput = AdminSchemas['ExtrasInput'];
export type CostInput = AdminSchemas['CostInput'];
export type PricingInput = AdminSchemas['PricingInput'];
export type CommissionInput = AdminSchemas['CommissionInput'];

export type TargetType = PublicSchemas['PackagePrice']['targetType'];
export type CostComponentType = CostInput['componentType'];
export type ServiceType = ItineraryInput['serviceType'];
export type BookingStatus = PublicSchemas['PackageBooking']['status'];
/** Status of a departure in the admin calendar (the public API never reports CANCELED). */
export type DepartureStatus = AdminSchemas['DepartureItem']['status'];
export type Weekday = AdminSchemas['DepartureItem']['weekday'];
