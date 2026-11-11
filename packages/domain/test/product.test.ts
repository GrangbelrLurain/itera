import { describe, expect, it } from 'vitest';
import { parseExtrasForm, parseItineraryRows, parseProductForm, parseScheduleForm } from '../src/product';
import { form } from './helpers';

const basic = {
  code: 'dad501',
  name: '다낭',
  nights: '4',
  days: '5',
  defaultCapacity: '30',
  active: true,
  price_ADULT_base: '1000000',
  price_ADULT_fuel: '90000',
  price_ADULT_tax: '45000',
  price_ADULT_sell: true,
  price_INFANT_base: '150000',
};

describe('parseProductForm', () => {
  it('maps the basic data, nulls blank optional text and keeps only priced traveler types', () => {
    const r = parseProductForm(form({ ...basic, grade: ' 프리미엄 ', hotelStars: '5', directFlight: true }));
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.value).toMatchObject({
      code: 'dad501',
      name: '다낭',
      grade: '프리미엄',
      nights: 4,
      days: 5,
      hotelStars: 5,
      hotelName: null,
      promotionTitle: null,
      directFlight: true,
      visaRequired: false,
      discountAmount: 0,
      defaultCapacity: 30,
      active: true,
    });
    expect(r.value.prices).toEqual([
      { targetType: 'ADULT', basePrice: 1000000, fuelSurcharge: 90000, taxesAndCharges: 45000, salesAvailable: true },
      { targetType: 'INFANT', basePrice: 150000, fuelSurcharge: 0, taxesAndCharges: 0, salesAvailable: false },
    ]);
  });

  it('reports a number field that is not a number', () => {
    expect(parseProductForm(form({ ...basic, nights: 'four' }))).toEqual({
      ok: false,
      error: '박 수은(는) 숫자로 입력하세요.',
    });
    expect(parseProductForm(form({ ...basic, defaultCapacity: '' })).ok).toBe(false);
    expect(parseProductForm(form({ ...basic, price_ADULT_base: 'x' })).ok).toBe(false);
  });
});

describe('parseItineraryRows', () => {
  it('maps rows and nulls blank text', () => {
    const r = parseItineraryRows(
      form({
        rows: [{ dayNo: '1', serviceType: 'FLIGHT', placeName: ' 인천 → 다낭 ', description: '', highlight: true }],
      }),
    );
    expect(r).toEqual({
      ok: true,
      value: [
        { dayNo: 1, serviceType: 'FLIGHT', placeName: '인천 → 다낭', description: null, highlight: true, perk: false },
      ],
    });
  });

  it('names a row whose day is not a number', () => {
    expect(parseItineraryRows(form({ rows: [{ dayNo: 'a', serviceType: 'MEAL' }] }))).toEqual({
      ok: false,
      error: '일정 1번째 행의 일차를 숫자로 입력하세요.',
    });
  });
});

describe('parseScheduleForm', () => {
  it('defaults rules to active, keeps weekdays and drops blank exclusion dates', () => {
    const r = parseScheduleForm(
      form({
        rules: [
          { validFrom: '2027-03-01', validTo: '2027-05-31', weekdays: ['WED', 'SAT'] },
          { validFrom: '2027-06-01', validTo: '2027-06-30', active: false },
        ],
        exclusions: [{ date: '2027-03-17' }, { date: ' ' }, {}],
      }),
    );
    expect(r).toEqual({
      ok: true,
      value: {
        rules: [
          { validFrom: '2027-03-01', validTo: '2027-05-31', weekdays: ['WED', 'SAT'], active: true },
          { validFrom: '2027-06-01', validTo: '2027-06-30', weekdays: [], active: false },
        ],
        exclusions: ['2027-03-17'],
      },
    });
  });
});

describe('parseExtrasForm', () => {
  it('stores no remark record when every remark field is blank', () => {
    const r = parseExtrasForm(form({ remark_priceNote: '   ' }));
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.value.remark).toBeNull();
  });

  it('keeps the remark record once any remark text is entered', () => {
    const r = parseExtrasForm(form({ remark_inclusions: '항공권, 호텔' }));
    expect(r.ok && r.value.remark).toMatchObject({ inclusions: '항공권, 호텔', priceNote: null, faq: null });
  });

  it('reads tours and shoppings, nulling blank text and numbers', () => {
    const r = parseExtrasForm(
      form({
        optionalTours: [{ name: '바나힐', placeName: '', priceAdult: '45.5', priceChild: '', included: true }],
        shoppings: [{ itemName: '커피', durationMinutes: '60', refundable: true }],
      }),
    );
    expect(r.ok).toBe(true);
    if (!r.ok) return;
    expect(r.value.optionalTours[0]).toMatchObject({
      name: '바나힐',
      placeName: null,
      priceAdult: 45.5,
      priceChild: null,
      included: true,
      preSale: false,
    });
    expect(r.value.shoppings[0]).toEqual({ itemName: '커피', placeName: null, durationMinutes: 60, refundable: true });
  });

  it('defaults the conditions and names a bad number by row or field', () => {
    const defaults = parseExtrasForm(form({}));
    expect(defaults.ok && defaults.value.condition).toMatchObject({
      shoppingCount: 0,
      singleRoomFee: 0,
      tourLeader: false,
      cancellationPolicy: null,
    });
    expect(parseExtrasForm(form({ cond_singleRoomFee: 'abc' }))).toEqual({
      ok: false,
      error: '싱글룸 비용은(는) 숫자로 입력하세요.',
    });
    expect(parseExtrasForm(form({ optionalTours: [{ name: 'a', priceAdult: 'x' }] }))).toEqual({
      ok: false,
      error: '옵션 투어 1번째 행: 성인 가격은(는) 숫자로 입력하세요.',
    });
  });
});
