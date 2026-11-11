import type { z } from 'zod';

/** Outcome of turning a submitted form into an API request. `error` is a message for the user. */
export type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };

export const ok = <T>(value: T): ParseResult<T> => ({ ok: true, value });
export const fail = (error: string): ParseResult<never> => ({ ok: false, error });

/** Trimmed text of a form field ('' when missing). */
export function str(formData: FormData, key: string): string {
  const v = formData.get(key);
  return typeof v === 'string' ? v.trim() : '';
}

/** Blank -> null (the contract marks optional text as nullable), otherwise the trimmed text. */
export function optStr(formData: FormData, key: string): string | null {
  const v = str(formData, key);
  return v === '' ? null : v;
}

/** Blank -> null; otherwise a number (NaN is kept so validation can report it). */
export function optNum(formData: FormData, key: string): number | null {
  const v = str(formData, key);
  return v === '' ? null : Number(v);
}

/** Checkbox: present as "on" when checked. */
export function flag(formData: FormData, key: string): boolean {
  return formData.get(key) === 'on';
}

/** A JSON list stored in a hidden input (the row editors); anything unreadable becomes an empty list. */
export function jsonList<T>(formData: FormData, key: string): T[] {
  try {
    const parsed = JSON.parse(str(formData, key) || '[]');
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    return [];
  }
}

/** All text fields of a submitted form, to refill the form after a failed submit. */
export function formValues(formData: FormData): Record<string, string> {
  const out: Record<string, string> = {};
  formData.forEach((v, k) => {
    if (typeof v === 'string') out[k] = v;
  });
  return out;
}

/** First problem of a zod result as a user message. */
export function firstIssue(error: z.ZodError): string {
  return error.issues[0]?.message ?? '입력값을 확인하세요.';
}

/** Blank or missing text of a row field -> null, otherwise trimmed. */
export function textOrNull(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const t = v.trim();
  return t === '' ? null : t;
}

/** Blank or missing -> null, otherwise Number(v) (NaN is kept so validation can report it). */
export function numOrNull(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null;
  return Number(v);
}
