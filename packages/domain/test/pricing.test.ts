import { describe, expect, it } from 'vitest';
import { parseCommissionRows, parseCostRows, parsePricingForm } from '../src/pricing';
import { form } from './helpers';

const row = (cost: string | number, extra: object = {}) => ({
  componentType: 'HOTEL',
  targetType: 'ADULT',
  cost,
  ...extra,
});

describe('parseCostRows', () => {
  it('reads rows, trims notes and nulls blank ones', () => {
    const r = parseCostRows(
      form({ costs: [row('450000', { note: '  peak  ' }), row(300000, { note: '   ' })] }),
      'costs',
    );
    expect(r).toEqual({
      ok: true,
      value: [
        { componentType: 'HOTEL', targetType: 'ADULT', cost: 450000, note: 'peak' },
        { componentType: 'HOTEL', targetType: 'ADULT', cost: 300000, note: null },
      ],
    });
  });

  it('is fine with no rows (clears the list)', () => {
    expect(parseCostRows(form({}), 'costs')).toEqual({ ok: true, value: [] });
  });

  it('names the row with the problem, with and without a unit', () => {
    const bad = form({ costs: [row('1'), row('')] });
    expect(parseCostRows(bad, 'costs', '원가')).toEqual({
      ok: false,
      error: '원가 2번째 행의 금액을 숫자로 입력하세요.',
    });
    expect(parseCostRows(bad, 'costs')).toEqual({ ok: false, error: '2번째 행의 금액을 숫자로 입력하세요.' });
    expect(parseCostRows(form({ costs: [row('abc')] }), 'costs').ok).toBe(false);
  });
});

describe('parsePricingForm', () => {
  it('combines costs with the mark-up (percent to basis points)', () => {
    const r = parsePricingForm(form({ marginPercent: '25', marginFixed: '10000', costs: [row('1000000')] }));
    expect(r).toEqual({
      ok: true,
      value: {
        costs: [{ componentType: 'HOTEL', targetType: 'ADULT', cost: 1000000, note: null }],
        marginBp: 2500,
        marginFixed: 10000,
      },
    });
  });

  it('treats blank margins as zero', () => {
    expect(parsePricingForm(form({}))).toEqual({ ok: true, value: { costs: [], marginBp: 0, marginFixed: 0 } });
  });

  it('rejects a bad margin rate, a bad fixed margin and a bad cost row', () => {
    expect(parsePricingForm(form({ marginPercent: '-5' }))).toEqual({
      ok: false,
      error: '마진율은 0 이상의 숫자여야 합니다.',
    });
    expect(parsePricingForm(form({ marginFixed: 'x' }))).toEqual({ ok: false, error: '고정 마진은 숫자여야 합니다.' });
    expect(parsePricingForm(form({ costs: [row('')] }))).toEqual({
      ok: false,
      error: '원가 1번째 행의 금액을 숫자로 입력하세요.',
    });
  });
});

describe('parseCommissionRows', () => {
  it('maps percent and fixed amounts, defaults to active and nulls a blank product code', () => {
    const r = parseCommissionRows(
      form({
        rows: [
          { channelCode: ' AGENCY_A ', productCode: '', commissionPercent: '8', commissionFixed: '' },
          {
            channelCode: 'PARTNER_C',
            productCode: 'DAD501',
            commissionPercent: '',
            commissionFixed: '20000',
            active: false,
          },
        ],
      }),
    );
    expect(r).toEqual({
      ok: true,
      value: [
        { channelCode: 'AGENCY_A', productCode: null, commissionBp: 800, commissionFixed: 0, active: true },
        { channelCode: 'PARTNER_C', productCode: 'DAD501', commissionBp: 0, commissionFixed: 20000, active: false },
      ],
    });
  });

  it('names the row with an invalid number', () => {
    const r = parseCommissionRows(
      form({
        rows: [
          { channelCode: 'A', commissionPercent: '1' },
          { channelCode: 'B', commissionPercent: 'x' },
        ],
      }),
    );
    expect(r).toEqual({ ok: false, error: '2번째 행의 수수료율/고정 수수료를 숫자로 입력하세요.' });
  });
});
