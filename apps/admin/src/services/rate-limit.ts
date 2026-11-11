/**
 * Request limits for a public demo. A fixed-window counter per client, kept in this process only: it stops casual
 * abuse and runaway scripts, it is not a replacement for the limit rule in front of the site (Cloudflare).
 */

export type Limit = { max: number; windowMs: number };

export type Verdict = { allowed: true } | { allowed: false; retryAfterSeconds: number };

type Window = { start: number; count: number };

export class RateLimiter {
  private readonly windows = new Map<string, Window>();
  private lastSweep = 0;

  constructor(
    private readonly limit: Limit,
    private readonly now: () => number = Date.now,
    private readonly maxKeys = 10_000,
  ) {}

  hit(key: string): Verdict {
    const t = this.now();
    this.sweep(t);

    const window = this.windows.get(key);
    if (!window || t - window.start >= this.limit.windowMs) {
      this.windows.set(key, { start: t, count: 1 });
      return { allowed: true };
    }
    if (window.count >= this.limit.max) {
      return {
        allowed: false,
        retryAfterSeconds: Math.max(1, Math.ceil((window.start + this.limit.windowMs - t) / 1000)),
      };
    }
    window.count += 1;
    return { allowed: true };
  }

  /** Drops finished windows now and then, so a flood of different clients cannot grow the map without bound. */
  private sweep(t: number) {
    if (t - this.lastSweep < this.limit.windowMs && this.windows.size < this.maxKeys) return;
    for (const [key, window] of this.windows) {
      if (t - window.start >= this.limit.windowMs) this.windows.delete(key);
    }
    if (this.windows.size >= this.maxKeys) this.windows.clear(); // still full of live windows: start over rather than leak
    this.lastSweep = t;
  }
}

/** "120" -> 120 requests per window; blank, 0 or anything that is not a positive number -> no limit. */
export function limitFromEnv(value: string | undefined, windowMs = 60_000): Limit | null {
  const max = Number(value?.trim());
  return Number.isFinite(max) && max > 0 ? { max: Math.floor(max), windowMs } : null;
}

/** GET, HEAD and OPTIONS only read; everything else (server actions are POSTs) changes something. */
export function isWrite(method: string): boolean {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase());
}

type HeaderReader = { get(name: string): string | null };

/**
 * Who is calling. Behind Cloudflare the address is in `CF-Connecting-Ip`; those headers can be forged by anyone who
 * reaches the server directly, so they are used only when the deployment says it sits behind a trusted proxy.
 * Otherwise every caller shares one bucket (a global limit).
 */
export function clientKey(headers: HeaderReader, trustProxy: boolean): string {
  if (!trustProxy) return 'shared';
  const candidate =
    headers.get('cf-connecting-ip') ?? headers.get('x-forwarded-for')?.split(',')[0] ?? headers.get('x-real-ip');
  const key = candidate?.trim().toLowerCase().slice(0, 64);
  return key ? key : 'shared';
}
