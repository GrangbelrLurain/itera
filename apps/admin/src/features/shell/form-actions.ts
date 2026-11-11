import { formValues, type ParseResult } from '@itera/domain/forms';
import type { FormState } from '@itera/ui';
import { revalidatePath } from 'next/cache';
import { ApiError } from '@/services/adminApi';

// Helpers of the Server Actions: they only orchestrate. The form is turned into an API request by
// @itera/domain, the backend decides.

/** A form that could not be turned into a request: show the message and keep what the user typed. */
export function invalid(error: string, formData: FormData): FormState {
  return { error, values: formValues(formData) };
}

/** The backend said no: show its message. Anything else (including Next.js redirects) is rethrown. */
export function failed(e: unknown, formData: FormData): FormState {
  if (e instanceof ApiError) return invalid(e.message, formData);
  throw e;
}

/** Parses nothing itself: runs `request` for an already parsed form and returns the state to show. */
export async function submit<T>(
  parsed: ParseResult<T>,
  formData: FormData,
  request: (value: T) => Promise<unknown>,
  message: (value: T) => string,
): Promise<FormState> {
  if (!parsed.ok) return invalid(parsed.error, formData);
  try {
    await request(parsed.value);
  } catch (e) {
    return failed(e, formData);
  }
  revalidatePath('/admin', 'layout');
  return { message: message(parsed.value) };
}
