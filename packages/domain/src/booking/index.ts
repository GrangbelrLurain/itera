import type { Tone } from '../tone';
import type { BookingStatus } from '../types';

/** In the order they are shown on the dashboard and in filters. */
export const BOOKING_STATUSES: readonly BookingStatus[] = ['PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED'];

export const BOOKING_STATUS_LABELS: Record<BookingStatus, string> = {
  PENDING_PAYMENT: '결제 대기',
  CONFIRMED: '예약 확정',
  CANCELLED: '취소됨',
  EXPIRED: '기한 만료',
};

export const BOOKING_STATUS_TONES: Record<BookingStatus, Tone> = {
  PENDING_PAYMENT: 'warning',
  CONFIRMED: 'success',
  CANCELLED: 'muted',
  EXPIRED: 'neutral',
};
