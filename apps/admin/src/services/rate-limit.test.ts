import { describe, expect, it } from 'vitest';
import { clientKey, isWrite, limitFromEnv, RateLimiter } from './rate-limit';

const clock = (start = 1_000_000) => {
  let now = start;
  return { now: () => now, advance: (ms: number) => (now += ms) };
};

describe('RateLimiter', () => {
  it('allows up to max requests per window, then refuses with the seconds left', () => {
    const c = clock();
    const limiter = new RateLimiter({ max: 3, windowMs: 60_000 }, c.now);
    expect([1, 2, 3].map(() => limiter.hit('a').allowed)).toEqual([true, true, true]);

    c.advance(20_000);
    expect(limiter.hit('a')).toEqual({ allowed: false, retryAfterSeconds: 40 });
  });

  it('starts a new window when the old one is over', () => {
    const c = clock();
    const limiter = new RateLimiter({ max: 1, windowMs: 60_000 }, c.now);
    expect(limiter.hit('a').allowed).toBe(true);
    expect(limiter.hit('a').allowed).toBe(false);
    c.advance(60_000);
    expect(limiter.hit('a').allowed).toBe(true);
  });

  it('counts every client on its own', () => {
    const limiter = new RateLimiter({ max: 1, windowMs: 60_000 }, clock().now);
    expect(limiter.hit('a').allowed).toBe(true);
    expect(limiter.hit('b').allowed).toBe(true);
    expect(limiter.hit('a').allowed).toBe(false);
    expect(limiter.hit('b').allowed).toBe(false);
  });

  it('never tells a client to retry in less than a second', () => {
    const c = clock();
    const limiter = new RateLimiter({ max: 1, windowMs: 1_000 }, c.now);
    limiter.hit('a');
    c.advance(999);
    expect(limiter.hit('a')).toEqual({ allowed: false, retryAfterSeconds: 1 });
  });

  it('does not grow without bound when many different clients arrive', () => {
    const c = clock();
    const limiter = new RateLimiter({ max: 5, windowMs: 60_000 }, c.now, 100);
    for (let i = 0; i < 1_000; i++) limiter.hit(`client-${i}`);
    // the map was cleared at the cap, so a client seen early is counted from zero again
    expect([1, 2, 3, 4, 5].map(() => limiter.hit('client-0').allowed)).toEqual([true, true, true, true, true]);
    expect(limiter.hit('client-0').allowed).toBe(false);
  });
});

describe('limitFromEnv', () => {
  it('reads a positive number of requests per window', () => {
    expect(limitFromEnv('120')).toEqual({ max: 120, windowMs: 60_000 });
    expect(limitFromEnv(' 30 ')).toEqual({ max: 30, windowMs: 60_000 });
    expect(limitFromEnv('2.9')).toEqual({ max: 2, windowMs: 60_000 });
  });

  it('means "no limit" for unset, zero, negative or garbage values', () => {
    for (const v of [undefined, '', '  ', '0', '-5', 'abc', 'NaN', 'Infinity']) {
      expect(limitFromEnv(v), String(v)).toBeNull();
    }
  });
});

describe('isWrite', () => {
  it('treats reads as reads and everything else as a write', () => {
    for (const m of ['GET', 'get', 'HEAD', 'OPTIONS']) expect(isWrite(m)).toBe(false);
    for (const m of ['POST', 'PUT', 'PATCH', 'DELETE']) expect(isWrite(m)).toBe(true);
  });
});

describe('clientKey', () => {
  const headers = (h: Record<string, string>) => ({ get: (name: string) => h[name.toLowerCase()] ?? null });

  it('uses one shared bucket unless the proxy headers are trusted', () => {
    expect(clientKey(headers({ 'cf-connecting-ip': '1.2.3.4' }), false)).toBe('shared');
  });

  it('prefers the Cloudflare address, then x-forwarded-for, then x-real-ip', () => {
    expect(clientKey(headers({ 'cf-connecting-ip': '1.2.3.4', 'x-forwarded-for': '9.9.9.9' }), true)).toBe('1.2.3.4');
    expect(clientKey(headers({ 'x-forwarded-for': '9.9.9.9, 10.0.0.1' }), true)).toBe('9.9.9.9');
    expect(clientKey(headers({ 'x-real-ip': '8.8.8.8' }), true)).toBe('8.8.8.8');
  });

  it('falls back to the shared bucket when no address is given, and caps the key length', () => {
    expect(clientKey(headers({}), true)).toBe('shared');
    expect(clientKey(headers({ 'cf-connecting-ip': '   ' }), true)).toBe('shared');
    expect(clientKey(headers({ 'cf-connecting-ip': 'x'.repeat(500) }), true)).toHaveLength(64);
  });
});
