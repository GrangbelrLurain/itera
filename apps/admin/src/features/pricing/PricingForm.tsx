'use client';

import { bpToPercent } from '@itera/domain/format';
import { COST_COMPONENTS, TARGET_LABELS, type TargetType } from '@itera/domain/pricing';
import {
  Card,
  type Column,
  emptyFormState,
  Field,
  FormGrid,
  FormMessage,
  type FormState,
  Input,
  Muted,
  RowsEditor,
  SubmitButton,
} from '@itera/ui';
import { useActionState } from 'react';
import type { CommissionInput, CostInput } from '@/services/adminApi';

type Action = (prev: FormState, formData: FormData) => Promise<FormState>;

const costColumns = (targets: TargetType[], amountLabel: string, noteLabel: string): Column[] => [
  { key: 'componentType', label: '원가 항목', type: 'select', options: COST_COMPONENTS, width: '130px' },
  {
    key: 'targetType',
    label: '대상',
    type: 'select',
    options: targets.map((t) => ({ value: t, label: TARGET_LABELS[t] })),
    width: '170px',
  },
  { key: 'cost', label: amountLabel, type: 'number', width: '190px' },
  { key: 'note', label: noteLabel, type: 'text' },
];

/** Cost components per traveler type plus the mark-up policy of a product. */
export function PricingForm({
  action,
  costs,
  marginBp,
  marginFixed,
  targets,
}: {
  action: Action;
  costs: CostInput[];
  marginBp: number;
  marginFixed: number;
  /** Traveler types that have a price row (only those can carry cost rows). */
  targets: TargetType[];
}) {
  const [state, formAction] = useActionState(action, emptyFormState);
  const v = state.values;
  return (
    <form action={formAction}>
      <FormMessage state={state} />

      <Card title="원가 구성">
        <Muted as="p">
          대상 구분(예: 성인)에 원가 행이 하나라도 있으면 그 구분의 판매가는 <strong>원가 합계 + 마진</strong>으로 자동
          계산되고, 없으면 기본 · 가격 탭의 수동 판매가를 그대로 씁니다. 유류할증료와 제세공과금은 원가에 넣지 않고 기본
          · 가격 탭의 값이 그대로 더해집니다.
        </Muted>
        <RowsEditor
          name="costs"
          columns={costColumns(targets, '원가 (1인, 원)', '메모')}
          initial={costs.map((c) => ({ ...c }))}
          emptyRow={{ componentType: 'FLIGHT', targetType: targets[0] ?? 'ADULT', cost: '', note: '' }}
          addLabel="+ 원가 항목 추가"
          emptyText="원가 항목이 없습니다. 모든 구분이 수동 판매가를 사용합니다."
        />
      </Card>

      <Card title="마진 (마크업)">
        <FormGrid>
          <Field label="마진율 (%, 원가 대비)">
            <Input
              name="marginPercent"
              type="number"
              step="any"
              min={0}
              max={1000}
              defaultValue={v ? (v.marginPercent ?? '') : bpToPercent(marginBp)}
            />
          </Field>
          <Field label="고정 마진 (1인, 원)">
            <Input
              name="marginFixed"
              type="number"
              min={0}
              defaultValue={v ? (v.marginFixed ?? '') : String(marginFixed)}
            />
          </Field>
        </FormGrid>
        <Muted as="p">
          판매가 = 원가 + 원가 × 마진율 + 고정 마진, <strong>100원 미만은 버립니다.</strong> 두 값을 모두 입력하면
          합산됩니다.
        </Muted>
      </Card>

      <div className="mt-3 flex items-center gap-3">
        <SubmitButton>원가 · 마진 저장</SubmitButton>
      </div>
    </form>
  );
}

/** Commission table of the sales channels (the whole table is edited and saved at once). */
export function CommissionForm({ action, rows }: { action: Action; rows: CommissionInput[] }) {
  const [state, formAction] = useActionState(action, emptyFormState);
  const columns: Column[] = [
    { key: 'channelCode', label: '채널 코드', type: 'text', placeholder: 'AGENCY_A', width: '170px' },
    { key: 'productCode', label: '상품 코드 (비우면 전체)', type: 'text', placeholder: '전체', width: '190px' },
    { key: 'commissionPercent', label: '수수료율 (%)', type: 'number', width: '120px' },
    { key: 'commissionFixed', label: '고정 수수료 (1인, 원)', type: 'number', width: '170px' },
    { key: 'active', label: '사용', type: 'checkbox', width: '56px' },
  ];
  return (
    <form action={formAction}>
      <FormMessage state={state} />
      <Card title="채널별 수수료">
        <Muted as="p">
          수수료는 <strong>할인 후 판매가</strong>에서 떼며(유류할증료·제세공과금 제외), 100원 미만은 버립니다. 같은
          채널이라도 <strong>상품 코드를 지정한 행이 전체 행보다 우선</strong>합니다. DIRECT(직접 판매)는 행이 없으면
          수수료가 0입니다.
        </Muted>
        <RowsEditor
          name="rows"
          columns={columns}
          initial={rows.map((r) => ({
            channelCode: r.channelCode,
            productCode: r.productCode ?? '',
            commissionPercent: bpToPercent(r.commissionBp),
            commissionFixed: String(r.commissionFixed ?? 0),
            active: r.active !== false,
          }))}
          emptyRow={{ channelCode: '', productCode: '', commissionPercent: '', commissionFixed: '', active: true }}
          addLabel="+ 수수료 행 추가"
          emptyText="등록된 채널 수수료가 없습니다."
          orderable={false}
        />
      </Card>
      <div className="mt-3 flex items-center gap-3">
        <SubmitButton>수수료 저장</SubmitButton>
      </div>
    </form>
  );
}

/** Cost adjustments of one departure date: same rows as the product costs, applied to this date only. */
export function CostOverrideForm({
  action,
  overrides,
  targets,
}: {
  action: Action;
  overrides: CostInput[];
  targets: TargetType[];
}) {
  const [state, formAction] = useActionState(action, emptyFormState);
  return (
    <form action={formAction}>
      <FormMessage state={state} />
      <RowsEditor
        name="costs"
        columns={costColumns(targets, '이 날짜의 원가 (1인, 원)', '메모 (예: 성수기)')}
        initial={overrides.map((c) => ({ ...c }))}
        emptyRow={{ componentType: 'HOTEL', targetType: targets[0] ?? 'ADULT', cost: '', note: '' }}
        addLabel="+ 원가 조정 추가"
        emptyText="이 날짜는 상품 기본 원가를 그대로 씁니다."
        orderable={false}
      />
      <div className="mt-3 flex items-center gap-3">
        <SubmitButton>원가 조정 저장</SubmitButton>
      </div>
    </form>
  );
}
