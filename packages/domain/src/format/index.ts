/** 1250000 -> "1,250,000원" */
export function won(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`;
}

/** Seoul wall-clock time for display (servers render in UTC). */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  return new Date(iso).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', hour12: false });
}

/** "12.5" -> 1250 basis points (1/100 %). Blank counts as 0; null when the text is not a non-negative number. */
export function percentToBp(text: string | null | undefined): number | null {
  const t = (text ?? '').toString().trim();
  if (t === '') return 0;
  const n = Number(t);
  if (!Number.isFinite(n) || n < 0) return null;
  return Math.round(n * 100);
}

/** 2500 basis points -> "25" (for form fields). */
export function bpToPercent(bp: number | null | undefined): string {
  return String((bp ?? 0) / 100);
}
