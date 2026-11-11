import { z } from 'zod';
import {
  fail,
  firstIssue,
  flag,
  jsonList,
  numOrNull,
  ok,
  optNum,
  optStr,
  type ParseResult,
  str,
  textOrNull,
} from '../forms';
import { TARGET_TYPES } from '../pricing';
import type {
  ConditionInput,
  ExtrasInput,
  ItineraryInput,
  OptionalTourInput,
  PriceInput,
  ProductInput,
  RemarkInput,
  RuleInput,
  ScheduleInput,
  ServiceType,
  ShoppingInput,
} from '../types';

export const SERVICE_TYPES: readonly { value: ServiceType; label: string }[] = [
  { value: 'FLIGHT', label: '항공' },
  { value: 'SIGHTSEEING', label: '관광' },
  { value: 'MEAL', label: '식사' },
  { value: 'HOTEL', label: '호텔' },
  { value: 'FREE', label: '자유' },
];

export const SERVICE_TYPE_LABELS: Record<ServiceType, string> = Object.fromEntries(
  SERVICE_TYPES.map((s) => [s.value, s.label]),
) as Record<ServiceType, string>;

/** A number that must really be a number (the rules of the value itself are checked by the backend). */
const num = (label: string) =>
  z.number(`${label}은(는) 숫자로 입력하세요.`).finite(`${label}은(는) 숫자로 입력하세요.`);
const optionalNum = (label: string) => num(label).nullable();

// ---- basic data + prices -------------------------------------------------------------------------

const priceSchema = z.object({
  targetType: z.enum(TARGET_TYPES as [string, ...string[]]),
  basePrice: num('기본 요금'),
  fuelSurcharge: num('유류할증료'),
  taxesAndCharges: num('제세공과금'),
  salesAvailable: z.boolean(),
});

const productSchema = z.object({
  code: z.string(),
  name: z.string(),
  grade: z.string().nullable(),
  nights: num('박 수'),
  days: num('일 수'),
  departureRoute: z.string().nullable(),
  departureTime: z.string().nullable(),
  returnRoute: z.string().nullable(),
  returnTime: z.string().nullable(),
  directFlight: z.boolean(),
  visaRequired: z.boolean(),
  meetingTime: z.string().nullable(),
  meetingLocation: z.string().nullable(),
  hotelName: z.string().nullable(),
  hotelStars: optionalNum('호텔 성급'),
  highlight: z.string().nullable(),
  promotionTitle: z.string().nullable(),
  discountAmount: num('할인액'),
  defaultCapacity: num('기본 정원'),
  active: z.boolean(),
  prices: z.array(priceSchema),
});

/** The product form: basic data plus one price row per traveler type that has a base price. */
export function parseProductForm(formData: FormData): ParseResult<ProductInput> {
  const prices: PriceInput[] = [];
  for (const t of TARGET_TYPES) {
    const base = optNum(formData, `price_${t}_base`);
    if (base === null) continue; // no base price entered: this traveler type is not offered
    prices.push({
      targetType: t,
      basePrice: base,
      fuelSurcharge: optNum(formData, `price_${t}_fuel`) ?? 0,
      taxesAndCharges: optNum(formData, `price_${t}_tax`) ?? 0,
      salesAvailable: flag(formData, `price_${t}_sell`),
    });
  }
  const parsed = productSchema.safeParse({
    code: str(formData, 'code'),
    name: str(formData, 'name'),
    grade: optStr(formData, 'grade'),
    nights: optNum(formData, 'nights'),
    days: optNum(formData, 'days'),
    departureRoute: optStr(formData, 'departureRoute'),
    departureTime: optStr(formData, 'departureTime'),
    returnRoute: optStr(formData, 'returnRoute'),
    returnTime: optStr(formData, 'returnTime'),
    directFlight: flag(formData, 'directFlight'),
    visaRequired: flag(formData, 'visaRequired'),
    meetingTime: optStr(formData, 'meetingTime'),
    meetingLocation: optStr(formData, 'meetingLocation'),
    hotelName: optStr(formData, 'hotelName'),
    hotelStars: optNum(formData, 'hotelStars'),
    highlight: optStr(formData, 'highlight'),
    promotionTitle: optStr(formData, 'promotionTitle'),
    discountAmount: optNum(formData, 'discountAmount') ?? 0,
    defaultCapacity: optNum(formData, 'defaultCapacity'),
    active: flag(formData, 'active'),
    prices,
  });
  return parsed.success ? ok(parsed.data as ProductInput) : fail(firstIssue(parsed.error));
}

// ---- itinerary -----------------------------------------------------------------------------------

interface RawItineraryRow {
  dayNo?: string | number;
  serviceType?: string;
  placeName?: string | null;
  description?: string | null;
  highlight?: boolean;
  perk?: boolean;
}

export function parseItineraryRows(formData: FormData, key = 'rows'): ParseResult<ItineraryInput[]> {
  const rows: ItineraryInput[] = [];
  for (const [i, r] of jsonList<RawItineraryRow>(formData, key).entries()) {
    const dayNo = Number(r.dayNo);
    if (!Number.isFinite(dayNo)) return fail(`일정 ${i + 1}번째 행의 일차를 숫자로 입력하세요.`);
    rows.push({
      dayNo,
      serviceType: r.serviceType as ServiceType,
      placeName: textOrNull(r.placeName),
      description: textOrNull(r.description),
      highlight: Boolean(r.highlight),
      perk: Boolean(r.perk),
    });
  }
  return ok(rows);
}

// ---- departure rules -----------------------------------------------------------------------------

interface RawRule {
  validFrom?: string;
  validTo?: string;
  weekdays?: string[];
  active?: boolean;
}

export function parseScheduleForm(formData: FormData): ParseResult<ScheduleInput> {
  const rules: RuleInput[] = jsonList<RawRule>(formData, 'rules').map((r) => ({
    validFrom: r.validFrom ?? '',
    validTo: r.validTo ?? '',
    weekdays: (r.weekdays ?? []) as RuleInput['weekdays'],
    active: r.active !== false,
  }));
  const exclusions = jsonList<{ date?: string }>(formData, 'exclusions')
    .map((e) => e.date)
    .filter((d): d is string => typeof d === 'string' && d.trim() !== '');
  return ok({ rules, exclusions });
}

// ---- optional tours, shopping, remarks, conditions -----------------------------------------------

const REMARK_KEYS = [
  'priceNote',
  'inclusions',
  'exclusions',
  'highlights',
  'bookingNotice',
  'roomInfo',
  'travelNotice',
  'faq',
] as const;

const tourSchema = z.object({
  name: z.string(),
  placeName: z.string().nullable(),
  nameEn: z.string().nullable(),
  summary: z.string().nullable(),
  details: z.string().nullable(),
  currency: z.string().nullable(),
  priceAdult: optionalNum('성인 가격'),
  priceChild: optionalNum('아동 가격'),
  minParticipants: optionalNum('최소 인원'),
  durationMinutes: optionalNum('소요 시간'),
  included: z.boolean(),
  preSale: z.boolean(),
});

const shoppingSchema = z.object({
  itemName: z.string(),
  placeName: z.string().nullable(),
  durationMinutes: optionalNum('쇼핑 시간'),
  refundable: z.boolean(),
});

const conditionSchema = z.object({
  airlineSeatClass: z.string().nullable(),
  tourLeader: z.boolean(),
  localGuide: z.string().nullable(),
  insuranceIncluded: z.boolean(),
  shoppingCount: num('쇼핑 횟수'),
  optionalTourIncluded: z.boolean(),
  admissionFeeIncluded: z.boolean(),
  localExpensesCurrency: z.string().nullable(),
  localExpensesAdult: num('현지 경비(성인)'),
  localExpensesChild: num('현지 경비(아동)'),
  localExpensesInfant: num('현지 경비(유아)'),
  singleRoomFee: num('싱글룸 비용'),
  minDeparturePopulation: num('최소 출발 인원'),
  cancellationPolicy: z.string().nullable(),
});

type RawTour = Record<keyof OptionalTourInput, unknown>;
type RawShopping = Record<keyof ShoppingInput, unknown>;

export function parseExtrasForm(formData: FormData): ParseResult<ExtrasInput> {
  const tours: OptionalTourInput[] = [];
  for (const [i, t] of jsonList<RawTour>(formData, 'optionalTours').entries()) {
    const parsed = tourSchema.safeParse({
      name: typeof t.name === 'string' ? t.name : '',
      placeName: textOrNull(t.placeName),
      nameEn: textOrNull(t.nameEn),
      summary: textOrNull(t.summary),
      details: textOrNull(t.details),
      currency: textOrNull(t.currency),
      priceAdult: numOrNull(t.priceAdult),
      priceChild: numOrNull(t.priceChild),
      minParticipants: numOrNull(t.minParticipants),
      durationMinutes: numOrNull(t.durationMinutes),
      included: Boolean(t.included),
      preSale: Boolean(t.preSale),
    });
    if (!parsed.success) return fail(`옵션 투어 ${i + 1}번째 행: ${firstIssue(parsed.error)}`);
    tours.push(parsed.data);
  }

  const shoppings: ShoppingInput[] = [];
  for (const [i, s] of jsonList<RawShopping>(formData, 'shoppings').entries()) {
    const parsed = shoppingSchema.safeParse({
      itemName: typeof s.itemName === 'string' ? s.itemName : '',
      placeName: textOrNull(s.placeName),
      durationMinutes: numOrNull(s.durationMinutes),
      refundable: Boolean(s.refundable),
    });
    if (!parsed.success) return fail(`쇼핑 ${i + 1}번째 행: ${firstIssue(parsed.error)}`);
    shoppings.push(parsed.data);
  }

  const remarkValues = Object.fromEntries(REMARK_KEYS.map((k) => [k, optStr(formData, `remark_${k}`)])) as RemarkInput;
  // The remark record is stored only when at least one text was entered.
  const remark = REMARK_KEYS.some((k) => remarkValues[k] !== null) ? remarkValues : null;

  const condition = conditionSchema.safeParse({
    airlineSeatClass: optStr(formData, 'cond_airlineSeatClass'),
    tourLeader: flag(formData, 'cond_tourLeader'),
    localGuide: optStr(formData, 'cond_localGuide'),
    insuranceIncluded: flag(formData, 'cond_insuranceIncluded'),
    shoppingCount: optNum(formData, 'cond_shoppingCount') ?? 0,
    optionalTourIncluded: flag(formData, 'cond_optionalTourIncluded'),
    admissionFeeIncluded: flag(formData, 'cond_admissionFeeIncluded'),
    localExpensesCurrency: optStr(formData, 'cond_localExpensesCurrency'),
    localExpensesAdult: optNum(formData, 'cond_localExpensesAdult') ?? 0,
    localExpensesChild: optNum(formData, 'cond_localExpensesChild') ?? 0,
    localExpensesInfant: optNum(formData, 'cond_localExpensesInfant') ?? 0,
    singleRoomFee: optNum(formData, 'cond_singleRoomFee') ?? 0,
    minDeparturePopulation: optNum(formData, 'cond_minDeparturePopulation') ?? 0,
    cancellationPolicy: optStr(formData, 'cond_cancellationPolicy'),
  });
  if (!condition.success) return fail(firstIssue(condition.error));

  return ok({ optionalTours: tours, shoppings, remark, condition: condition.data as ConditionInput });
}
