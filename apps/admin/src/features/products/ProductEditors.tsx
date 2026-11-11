'use client';

import { WEEKDAYS } from '@itera/domain/departure';
import { SERVICE_TYPES } from '@itera/domain/product';
import {
  Card,
  Check,
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
  Textarea,
} from '@itera/ui';
import { useActionState } from 'react';
import type {
  ConditionInput,
  ItineraryInput,
  OptionalTourInput,
  RemarkInput,
  RuleInput,
  ScheduleInput,
  ShoppingInput,
} from '@/services/adminApi';

type Action = (prev: FormState, formData: FormData) => Promise<FormState>;

const SubmitRow = ({ children }: { children: React.ReactNode }) => (
  <div className="mt-3 flex items-center gap-3">
    <SubmitButton>{children}</SubmitButton>
  </div>
);

// ---- itinerary --------------------------------------------------------------------------------

export function ItineraryForm({ action, initial, days }: { action: Action; initial: ItineraryInput[]; days: number }) {
  const [state, formAction] = useActionState(action, emptyFormState);
  const columns: Column[] = [
    { key: 'dayNo', label: `일차 (1~${days})`, type: 'number', width: '90px' },
    { key: 'serviceType', label: '구분', type: 'select', options: SERVICE_TYPES, width: '100px' },
    { key: 'placeName', label: '장소', type: 'text' },
    { key: 'description', label: '설명', type: 'textarea' },
    { key: 'highlight', label: '핵심', type: 'checkbox', width: '56px' },
    { key: 'perk', label: '특전', type: 'checkbox', width: '56px' },
  ];
  return (
    <form action={formAction}>
      <FormMessage state={state} />
      <Card title="일정">
        <Muted as="p">
          일차별로 항목을 추가합니다. 같은 일차 안의 순서는 위에서부터입니다. 실제 날짜는 출발일에서 계산됩니다(출발일 +
          일차 − 1).
        </Muted>
        <RowsEditor
          name="rows"
          columns={columns}
          initial={initial.map((r) => ({ ...r }))}
          emptyRow={{
            dayNo: 1,
            serviceType: 'SIGHTSEEING',
            placeName: '',
            description: '',
            highlight: false,
            perk: false,
          }}
          addLabel="+ 일정 항목 추가"
          emptyText="등록된 일정이 없습니다."
        />
      </Card>
      <SubmitRow>일정 저장</SubmitRow>
    </form>
  );
}

// ---- schedule (departure rules) ---------------------------------------------------------------

export function ScheduleForm({ action, initial }: { action: Action; initial: ScheduleInput }) {
  const [state, formAction] = useActionState(action, emptyFormState);
  const ruleColumns: Column[] = [
    { key: 'validFrom', label: '시작일', type: 'date', width: '150px' },
    { key: 'validTo', label: '종료일', type: 'date', width: '150px' },
    { key: 'weekdays', label: '출발 요일', type: 'chips', options: WEEKDAYS },
    { key: 'active', label: '사용', type: 'checkbox', width: '56px' },
  ];
  const today = new Date().toISOString().slice(0, 10);
  return (
    <form action={formAction}>
      <FormMessage state={state} />
      <Card title="출발 규칙">
        <Muted as="p">
          기간 안에서 선택한 요일마다 출발일이 만들어집니다. 규칙을 바꾸거나 지워도 이미 예약이 있거나 관리자가 조치한
          날짜는 그대로 유지됩니다.
        </Muted>
        <RowsEditor
          name="rules"
          columns={ruleColumns}
          initial={initial.rules.map((r: RuleInput) => ({ ...r, active: r.active !== false }))}
          emptyRow={{ validFrom: today, validTo: today, weekdays: [], active: true }}
          addLabel="+ 규칙 추가"
          emptyText="출발 규칙이 없습니다. 규칙이 없으면 출발일이 만들어지지 않습니다."
          orderable={false}
        />
      </Card>
      <Card title="제외일">
        <Muted as="p">규칙에서 빼고 싶은 날짜입니다 (예: 공휴일). 이미 예약이 있는 날짜는 제외해도 유지됩니다.</Muted>
        <RowsEditor
          name="exclusions"
          columns={[{ key: 'date', label: '날짜', type: 'date', width: '170px' }]}
          initial={initial.exclusions.map((d) => ({ date: d }))}
          emptyRow={{ date: today }}
          addLabel="+ 제외일 추가"
          emptyText="제외일이 없습니다."
          orderable={false}
        />
      </Card>
      <SubmitRow>출발 규칙 저장</SubmitRow>
    </form>
  );
}

// ---- extras (optional tours, shopping, remark, conditions) -----------------------------------

const REMARK_FIELDS: { key: keyof RemarkInput; label: string }[] = [
  { key: 'priceNote', label: '가격 안내' },
  { key: 'inclusions', label: '포함 사항' },
  { key: 'exclusions', label: '불포함 사항' },
  { key: 'highlights', label: '상품 포인트' },
  { key: 'bookingNotice', label: '예약 시 유의사항' },
  { key: 'roomInfo', label: '객실 정보' },
  { key: 'travelNotice', label: '여행 시 유의사항' },
  { key: 'faq', label: '자주 묻는 질문' },
];

const num = (v: number | null | undefined) => (v === null || v === undefined ? '' : String(v));

export function ExtrasForm({
  action,
  tours,
  shoppings,
  remark,
  condition,
}: {
  action: Action;
  tours: OptionalTourInput[];
  shoppings: ShoppingInput[];
  remark: RemarkInput | null | undefined;
  condition: ConditionInput | null | undefined;
}) {
  const [state, formAction] = useActionState(action, emptyFormState);
  const r: Partial<RemarkInput> = remark ?? {};
  const c: Partial<ConditionInput> = condition ?? {};

  return (
    <form action={formAction}>
      <FormMessage state={state} />

      <Card title="선택관광">
        <RowsEditor
          name="optionalTours"
          columns={[
            { key: 'name', label: '이름 *', type: 'text' },
            { key: 'placeName', label: '장소', type: 'text' },
            { key: 'currency', label: '통화', type: 'text', placeholder: 'USD', width: '80px' },
            { key: 'priceAdult', label: '성인', type: 'number', width: '90px' },
            { key: 'priceChild', label: '아동', type: 'number', width: '90px' },
            { key: 'minParticipants', label: '최소인원', type: 'number', width: '90px' },
            { key: 'durationMinutes', label: '소요(분)', type: 'number', width: '90px' },
            { key: 'summary', label: '한줄 설명', type: 'text' },
            { key: 'included', label: '포함', type: 'checkbox', width: '56px' },
            { key: 'preSale', label: '사전판매', type: 'checkbox', width: '70px' },
          ]}
          initial={tours.map((t) => ({ ...t }))}
          emptyRow={{ name: '', currency: 'USD', included: false, preSale: false }}
          addLabel="+ 선택관광 추가"
          emptyText="등록된 선택관광이 없습니다."
        />
      </Card>

      <Card title="쇼핑">
        <RowsEditor
          name="shoppings"
          columns={[
            { key: 'itemName', label: '품목 *', type: 'text' },
            { key: 'placeName', label: '장소', type: 'text' },
            { key: 'durationMinutes', label: '소요(분)', type: 'number', width: '90px' },
            { key: 'refundable', label: '환불', type: 'checkbox', width: '56px' },
          ]}
          initial={shoppings.map((s) => ({ ...s }))}
          emptyRow={{ itemName: '', refundable: false }}
          addLabel="+ 쇼핑 추가"
          emptyText="등록된 쇼핑이 없습니다."
        />
      </Card>

      <Card title="포함 · 불포함 · 유의사항">
        <div className="grid gap-3">
          {REMARK_FIELDS.map((f) => (
            <Field key={f.key} label={f.label}>
              <Textarea name={`remark_${f.key}`} rows={3} defaultValue={r[f.key] ?? ''} />
            </Field>
          ))}
        </div>
      </Card>

      <Card title="조건">
        <FormGrid>
          <Field label="항공 좌석 등급">
            <Input name="cond_airlineSeatClass" defaultValue={c.airlineSeatClass ?? ''} />
          </Field>
          <Field label="현지 가이드">
            <Input name="cond_localGuide" defaultValue={c.localGuide ?? ''} />
          </Field>
          <Field label="쇼핑 횟수">
            <Input name="cond_shoppingCount" type="number" min={0} defaultValue={num(c.shoppingCount)} />
          </Field>
          <Field label="최소 출발 인원">
            <Input
              name="cond_minDeparturePopulation"
              type="number"
              min={0}
              defaultValue={num(c.minDeparturePopulation)}
            />
          </Field>
          <Field label="싱글룸 사용료">
            <Input name="cond_singleRoomFee" type="number" min={0} defaultValue={num(c.singleRoomFee)} />
          </Field>
          <Field label="현지 필수 경비 통화">
            <Input name="cond_localExpensesCurrency" defaultValue={c.localExpensesCurrency ?? ''} placeholder="USD" />
          </Field>
          <Field label="현지 경비 · 성인">
            <Input name="cond_localExpensesAdult" type="number" min={0} defaultValue={num(c.localExpensesAdult)} />
          </Field>
          <Field label="현지 경비 · 아동">
            <Input name="cond_localExpensesChild" type="number" min={0} defaultValue={num(c.localExpensesChild)} />
          </Field>
          <Field label="현지 경비 · 유아">
            <Input name="cond_localExpensesInfant" type="number" min={0} defaultValue={num(c.localExpensesInfant)} />
          </Field>
          <Check name="cond_tourLeader" defaultChecked={c.tourLeader ?? false}>
            인솔자 동행
          </Check>
          <Check name="cond_insuranceIncluded" defaultChecked={c.insuranceIncluded ?? false}>
            여행자 보험 포함
          </Check>
          <Check name="cond_optionalTourIncluded" defaultChecked={c.optionalTourIncluded ?? false}>
            선택관광 포함
          </Check>
          <Check name="cond_admissionFeeIncluded" defaultChecked={c.admissionFeeIncluded ?? false}>
            관광지 입장료 포함
          </Check>
        </FormGrid>
        <Field label="취소 약관">
          <Textarea name="cond_cancellationPolicy" rows={3} defaultValue={c.cancellationPolicy ?? ''} />
        </Field>
      </Card>

      <SubmitRow>부가 정보 저장</SubmitRow>
    </form>
  );
}
