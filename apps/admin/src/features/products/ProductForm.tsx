'use client';

import { TARGET_LABELS, TARGET_TYPES } from '@itera/domain/pricing';
import {
  Card,
  Check,
  emptyFormState,
  Field,
  FormGrid,
  FormMessage,
  type FormState,
  Input,
  Muted,
  SubmitButton,
  Table,
} from '@itera/ui';
import { useActionState } from 'react';
import type { ProductInput } from '@/services/adminApi';

type Action = (prev: FormState, formData: FormData) => Promise<FormState>;

/** Create / edit form for the basic data and base prices. Inputs keep their values after a failed save. */
export function ProductForm({
  action,
  initial,
  mode,
}: {
  action: Action;
  initial: ProductInput | null;
  mode: 'create' | 'edit';
}) {
  const [state, formAction] = useActionState(action, emptyFormState);
  const v = state.values;

  // Value shown in a field: what the user submitted last time, otherwise the saved value.
  const text = (key: string, saved: unknown) =>
    v ? (v[key] ?? '') : saved === null || saved === undefined ? '' : String(saved);
  const checked = (key: string, saved: boolean | null | undefined, fallback = false) =>
    v ? v[key] === 'on' : (saved ?? fallback);
  const priceOf = (t: string) => initial?.prices.find((p) => p.targetType === t);
  const price = (t: string, field: 'base' | 'fuel' | 'tax') => {
    const key = `price_${t}_${field}`;
    if (v) return v[key] ?? '';
    const p = priceOf(t);
    if (!p) return '';
    return String(
      field === 'base' ? p.basePrice : field === 'fuel' ? (p.fuelSurcharge ?? 0) : (p.taxesAndCharges ?? 0),
    );
  };

  return (
    <form action={formAction} key={state.message ?? 'form'}>
      <FormMessage state={state} />

      <Card title="기본 정보">
        <FormGrid>
          <Field label="상품 코드 *">
            <Input
              name="code"
              defaultValue={text('code', initial?.code)}
              readOnly={mode === 'edit'}
              required={mode === 'create'}
              maxLength={20}
              placeholder="예: DAD501"
              className={mode === 'edit' ? 'bg-surface-subtle' : undefined}
            />
          </Field>
          <Field label="상품명 *" className="col-span-2">
            <Input name="name" defaultValue={text('name', initial?.name)} required maxLength={100} />
          </Field>
          <Field label="등급">
            <Input
              name="grade"
              defaultValue={text('grade', initial?.grade)}
              placeholder="예: 프리미엄"
              maxLength={30}
            />
          </Field>
          <Field label="박 *">
            <Input
              name="nights"
              type="number"
              min={0}
              max={60}
              defaultValue={text('nights', initial?.nights ?? 3)}
              required
            />
          </Field>
          <Field label="일 *">
            <Input
              name="days"
              type="number"
              min={1}
              max={61}
              defaultValue={text('days', initial?.days ?? 4)}
              required
            />
          </Field>
          <Field label="기본 정원 (석) *">
            <Input
              name="defaultCapacity"
              type="number"
              min={1}
              max={999}
              defaultValue={text('defaultCapacity', initial?.defaultCapacity ?? 30)}
              required
            />
          </Field>
          <Field label="하이라이트" className="col-span-2">
            <Input name="highlight" defaultValue={text('highlight', initial?.highlight)} maxLength={500} />
          </Field>
          <Field label="프로모션 이름">
            <Input
              name="promotionTitle"
              defaultValue={text('promotionTitle', initial?.promotionTitle)}
              maxLength={200}
            />
          </Field>
          <Field label="할인액 (1인, 유아 제외)">
            <Input
              name="discountAmount"
              type="number"
              min={0}
              defaultValue={text('discountAmount', initial?.discountAmount ?? 0)}
            />
          </Field>
          <Check name="active" defaultChecked={checked('active', initial?.active, true)}>
            판매 활성
          </Check>
        </FormGrid>
        <Muted as="p">
          기본 정원은 출발일이 처음 저장될 때 그 날짜의 정원으로 복사됩니다. 이미 저장된 출발일의 정원은 출발일 관리에서
          바꿉니다.
        </Muted>
      </Card>

      <Card title="항공 · 미팅 · 숙박">
        <FormGrid>
          <Field label="출발 노선">
            <Input
              name="departureRoute"
              defaultValue={text('departureRoute', initial?.departureRoute)}
              placeholder="ICN-DAD"
              maxLength={20}
            />
          </Field>
          <Field label="출발 시간">
            <Input name="departureTime" type="time" defaultValue={text('departureTime', initial?.departureTime)} />
          </Field>
          <Field label="귀국 노선">
            <Input
              name="returnRoute"
              defaultValue={text('returnRoute', initial?.returnRoute)}
              placeholder="DAD-ICN"
              maxLength={20}
            />
          </Field>
          <Field label="귀국 도착 시간">
            <Input name="returnTime" type="time" defaultValue={text('returnTime', initial?.returnTime)} />
          </Field>
          <Check name="directFlight" defaultChecked={checked('directFlight', initial?.directFlight)}>
            직항
          </Check>
          <Check name="visaRequired" defaultChecked={checked('visaRequired', initial?.visaRequired)}>
            비자 필수
          </Check>
          <Field label="미팅 시간">
            <Input name="meetingTime" type="time" defaultValue={text('meetingTime', initial?.meetingTime)} />
          </Field>
          <Field label="미팅 장소" className="col-span-2">
            <Input
              name="meetingLocation"
              defaultValue={text('meetingLocation', initial?.meetingLocation)}
              maxLength={100}
            />
          </Field>
          <Field label="대표 호텔" className="col-span-2">
            <Input name="hotelName" defaultValue={text('hotelName', initial?.hotelName)} maxLength={200} />
          </Field>
          <Field label="호텔 성급">
            <Input
              name="hotelStars"
              type="number"
              min={1}
              max={5}
              defaultValue={text('hotelStars', initial?.hotelStars)}
            />
          </Field>
        </FormGrid>
      </Card>

      <Card title="기본 가격 (1인)">
        <Table>
          <thead>
            <tr>
              <th>구분</th>
              <th className="text-right">수동 판매가</th>
              <th className="text-right">유류할증료</th>
              <th className="text-right">제세공과금</th>
              <th>판매</th>
            </tr>
          </thead>
          <tbody>
            {TARGET_TYPES.map((t) => {
              const required = t === 'ADULT';
              return (
                <tr key={t}>
                  <td>
                    {TARGET_LABELS[t]}
                    {required && ' *'}
                  </td>
                  <td>
                    <Input
                      name={`price_${t}_base`}
                      type="number"
                      min={0}
                      defaultValue={price(t, 'base')}
                      required={required}
                      placeholder={required ? '필수' : '비우면 판매 안 함'}
                      className="min-w-[110px]"
                    />
                  </td>
                  <td>
                    <Input
                      name={`price_${t}_fuel`}
                      type="number"
                      min={0}
                      defaultValue={price(t, 'fuel')}
                      placeholder="0"
                      className="min-w-[110px]"
                    />
                  </td>
                  <td>
                    <Input
                      name={`price_${t}_tax`}
                      type="number"
                      min={0}
                      defaultValue={price(t, 'tax')}
                      placeholder="0"
                      className="min-w-[110px]"
                    />
                  </td>
                  <td>
                    <input
                      name={`price_${t}_sell`}
                      type="checkbox"
                      className="size-4 accent-primary"
                      defaultChecked={v ? v[`price_${t}_sell`] === 'on' : (priceOf(t)?.salesAvailable ?? true)}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </Table>
        <Muted as="p">
          유류할증료와 제세공과금은 원가 그대로 받는 통과 항목입니다(마진·수수료 대상 아님). 수동 판매가는{' '}
          <strong>원가 · 마진 탭에 원가가 입력되지 않은 구분</strong>에만 쓰이고, 원가가 입력된 구분은 판매가가 자동
          계산됩니다.
        </Muted>
      </Card>

      <div className="mt-3 flex items-center gap-3">
        <SubmitButton>{mode === 'create' ? '상품 등록' : '저장'}</SubmitButton>
      </div>
    </form>
  );
}
