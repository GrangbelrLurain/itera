import { describe, expect, it } from 'vitest';
import { bpToPercent, formatDateTime, percentToBp, won } from '../src/format';

describe('format', () => {
  it('formats won with thousands separators', () => {
    expect(won(1250000)).toBe('1,250,000원');
    expect(won(0)).toBe('0원');
  });

  it('shows Seoul wall-clock time and a dash for missing values', () => {
    expect(formatDateTime('2026-11-10T05:36:00Z')).toContain('14');
    expect(formatDateTime(null)).toBe('-');
    expect(formatDateTime(undefined)).toBe('-');
  });

  it('turns percent text into basis points', () => {
    expect(percentToBp('25')).toBe(2500);
    expect(percentToBp('12.5')).toBe(1250);
    expect(percentToBp('')).toBe(0);
    expect(percentToBp(null)).toBe(0);
    expect(percentToBp('-1')).toBeNull();
    expect(percentToBp('abc')).toBeNull();
    expect(percentToBp('0.005')).toBe(1); // rounds to the nearest basis point
  });

  it('turns basis points back into a form value', () => {
    expect(bpToPercent(2500)).toBe('25');
    expect(bpToPercent(1250)).toBe('12.5');
    expect(bpToPercent(null)).toBe('0');
  });
});
