import type { Tone } from '../tone';
import type { DepartureStatus, Weekday } from '../types';

export const DEPARTURE_STATUS_LABELS: Record<DepartureStatus, string> = {
  OPEN: '모객중',
  FULL: '모객완료',
  CLOSED: '마감',
  CANCELED: '출발 취소',
};

/** The public package list calls a closed departure "단체마감". */
export const PUBLIC_DEPARTURE_STATUS_LABELS: Record<Exclude<DepartureStatus, 'CANCELED'>, string> = {
  OPEN: '모객중',
  FULL: '모객완료',
  CLOSED: '단체마감',
};

export const DEPARTURE_STATUS_TONES: Record<DepartureStatus, Tone> = {
  OPEN: 'success',
  FULL: 'warning',
  CLOSED: 'muted',
  CANCELED: 'neutral',
};

/** Filter values of the departure search (lower case, as the public API expects). */
export const DEPARTURE_STATUS_FILTERS = [
  { value: 'open', label: '모객중' },
  { value: 'full', label: '모객완료' },
  { value: 'closed', label: '단체마감' },
] as const;

export const WEEKDAYS: readonly { value: Weekday; label: string }[] = [
  { value: 'MON', label: '월' },
  { value: 'TUE', label: '화' },
  { value: 'WED', label: '수' },
  { value: 'THU', label: '목' },
  { value: 'FRI', label: '금' },
  { value: 'SAT', label: '토' },
  { value: 'SUN', label: '일' },
];

export const WEEKDAY_LABELS: Record<Weekday, string> = Object.fromEntries(
  WEEKDAYS.map((d) => [d.value, d.label]),
) as Record<Weekday, string>;

/** yyyy-MM-dd plus a number of months (calendar arithmetic in UTC, so it never depends on the machine's zone). */
export function addMonths(iso: string, months: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCMonth(d.getUTCMonth() + months);
  return d.toISOString().slice(0, 10);
}

/**
 * Start of the previous window in the departure calendar: `windowMonths` before `from`,
 * but never earlier than today. null when the calendar is already at today (no previous window).
 */
export function previousWindowStart(from: string, today: string, windowMonths: number): string | null {
  if (from <= today) return null;
  const prev = addMonths(from, -windowMonths);
  return prev > today ? prev : today;
}
