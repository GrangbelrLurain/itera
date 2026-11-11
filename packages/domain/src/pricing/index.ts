import { z } from 'zod';
import { bpToPercent, percentToBp } from '../format';
import { fail, firstIssue, jsonList, ok, type ParseResult, str, textOrNull } from '../forms';
import type { CommissionInput, CostComponentType, CostInput, PricingInput, TargetType } from '../types';

/** In the order they are shown. */
export const TARGET_TYPES: readonly TargetType[] = ['ADULT', 'NOBED_CHILD', 'EXTRABED_CHILD', 'INFANT'];

export const TARGET_LABELS: Record<TargetType, string> = {
  ADULT: '성인',
  NOBED_CHILD: '소아 (노베드)',
  EXTRABED_CHILD: '소아 (엑스트라베드)',
  INFANT: '유아',
};

export const COST_COMPONENTS: readonly { value: CostComponentType; label: string }[] = [
  { value: 'FLIGHT', label: '항공' },
  { value: 'HOTEL', label: '호텔' },
  { value: 'LAND', label: '현지(랜드)' },
  { value: 'TOUR', label: '관광' },
  { value: 'INSURANCE', label: '보험' },
  { value: 'OTHER', label: '기타' },
];

export const COST_COMPONENT_LABELS: Record<CostComponentType, string> = Object.fromEntries(
  COST_COMPONENTS.map((c) => [c.value, c.label]),
) as Record<CostComponentType, string>;

export type { CostComponentType, TargetType } from '../types';
export { bpToPercent, percentToBp };

// ---- cost rows (product costs and per-date overrides) ------------------------------------------

interface RawCostRow {
  componentType?: string;
  targetType?: string;
  cost?: string | number | null;
  note?: string | null;
}

/**
 * The cost rows of a row editor (JSON in a hidden input). `unit` names the row in the message,
 * e.g. "원가" -> "원가 2번째 행의 금액을 숫자로 입력하세요."
 */
export function parseCostRows(formData: FormData, key: string, unit = ''): ParseResult<CostInput[]> {
  const rows: CostInput[] = [];
  for (const [i, r] of jsonList<RawCostRow>(formData, key).entries()) {
    const cost = r.cost === '' || r.cost === undefined || r.cost === null ? Number.NaN : Number(r.cost);
    if (!Number.isFinite(cost)) {
      return fail(`${unit ? `${unit} ` : ''}${i + 1}번째 행의 금액을 숫자로 입력하세요.`);
    }
    rows.push({
      componentType: r.componentType as CostComponentType,
      targetType: r.targetType as TargetType,
      cost,
      note: textOrNull(r.note),
    });
  }
  return ok(rows);
}

// ---- cost + mark-up of a product ----------------------------------------------------------------

const marginSchema = z.object({
  marginBp: z.number('마진율은 0 이상의 숫자여야 합니다.'),
  marginFixed: z.number('고정 마진은 숫자여야 합니다.').finite('고정 마진은 숫자여야 합니다.'),
});

export function parsePricingForm(formData: FormData): ParseResult<PricingInput> {
  const marginBp = percentToBp(str(formData, 'marginPercent'));
  if (marginBp === null) return fail('마진율은 0 이상의 숫자여야 합니다.');
  const fixedText = str(formData, 'marginFixed');
  const margin = marginSchema.safeParse({ marginBp, marginFixed: fixedText === '' ? 0 : Number(fixedText) });
  if (!margin.success) return fail(firstIssue(margin.error));

  const costs = parseCostRows(formData, 'costs', '원가');
  if (!costs.ok) return costs;
  return ok({ costs: costs.value, ...margin.data });
}

// ---- channel commissions -------------------------------------------------------------------------

interface RawCommissionRow {
  channelCode?: string;
  productCode?: string | null;
  commissionPercent?: string | number | null;
  commissionFixed?: string | number | null;
  active?: boolean;
}

export function parseCommissionRows(formData: FormData, key = 'rows'): ParseResult<CommissionInput[]> {
  const rows: CommissionInput[] = [];
  for (const [i, r] of jsonList<RawCommissionRow>(formData, key).entries()) {
    const bp = percentToBp(
      r.commissionPercent === undefined || r.commissionPercent === null ? '' : String(r.commissionPercent),
    );
    const fixed =
      r.commissionFixed === undefined || r.commissionFixed === null || r.commissionFixed === ''
        ? 0
        : Number(r.commissionFixed);
    if (bp === null || !Number.isFinite(fixed)) {
      return fail(`${i + 1}번째 행의 수수료율/고정 수수료를 숫자로 입력하세요.`);
    }
    rows.push({
      channelCode: (r.channelCode ?? '').trim(),
      productCode: textOrNull(r.productCode),
      commissionBp: bp,
      commissionFixed: fixed,
      active: r.active !== false,
    });
  }
  return ok(rows);
}
