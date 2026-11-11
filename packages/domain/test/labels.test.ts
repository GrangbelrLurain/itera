import { describe, expect, it } from 'vitest';
import { BOOKING_STATUS_LABELS, BOOKING_STATUS_TONES, BOOKING_STATUSES } from '../src/booking';
import {
  addMonths,
  DEPARTURE_STATUS_LABELS,
  DEPARTURE_STATUS_TONES,
  previousWindowStart,
  WEEKDAY_LABELS,
  WEEKDAYS,
} from '../src/departure';
import { COST_COMPONENT_LABELS, COST_COMPONENTS, TARGET_LABELS, TARGET_TYPES } from '../src/pricing';
import { SERVICE_TYPE_LABELS, SERVICE_TYPES } from '../src/product';

describe('labels', () => {
  it('has a label and tone for every booking status', () => {
    for (const s of BOOKING_STATUSES) {
      expect(BOOKING_STATUS_LABELS[s]).toBeTruthy();
      expect(BOOKING_STATUS_TONES[s]).toBeTruthy();
    }
    expect(BOOKING_STATUSES).toHaveLength(4);
  });

  it('has a label and tone for every departure status', () => {
    for (const s of ['OPEN', 'FULL', 'CLOSED', 'CANCELED'] as const) {
      expect(DEPARTURE_STATUS_LABELS[s]).toBeTruthy();
      expect(DEPARTURE_STATUS_TONES[s]).toBeTruthy();
    }
  });

  it('keeps Monday-first weekdays with matching label lookups', () => {
    expect(WEEKDAYS.map((d) => d.value)).toEqual(['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']);
    expect(WEEKDAY_LABELS.SAT).toBe('토');
  });

  it('labels every traveler type, cost component and service type', () => {
    expect(TARGET_TYPES.map((t) => TARGET_LABELS[t])).toEqual(['성인', '소아 (노베드)', '소아 (엑스트라베드)', '유아']);
    expect(COST_COMPONENTS.every((c) => COST_COMPONENT_LABELS[c.value] === c.label)).toBe(true);
    expect(SERVICE_TYPES.every((s) => SERVICE_TYPE_LABELS[s.value] === s.label)).toBe(true);
  });
});

describe('departure calendar windows', () => {
  it('adds months in calendar terms', () => {
    expect(addMonths('2027-01-31', 1)).toBe('2027-03-03'); // JS date overflow, same as the previous behavior
    expect(addMonths('2027-03-01', 3)).toBe('2027-06-01');
    expect(addMonths('2027-03-01', -3)).toBe('2026-12-01');
  });

  it('goes back one window but never before today', () => {
    expect(previousWindowStart('2027-06-01', '2027-01-04', 3)).toBe('2027-03-01');
    expect(previousWindowStart('2027-03-01', '2027-01-04', 3)).toBe('2027-01-04'); // would be 2026-12-01
    expect(previousWindowStart('2027-01-04', '2027-01-04', 3)).toBeNull(); // already at today
  });
});
