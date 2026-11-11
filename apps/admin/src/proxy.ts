import { type NextRequest, NextResponse } from 'next/server';
import { clientKey, isWrite, limitFromEnv, RateLimiter } from '@/services/rate-limit';

/**
 * Optional request limit for a public demo, off unless configured:
 *   RATE_LIMIT_READS_PER_MINUTE   page loads per client per minute
 *   RATE_LIMIT_WRITES_PER_MINUTE  form submits (server actions, login) per client per minute
 *   TRUST_PROXY_HEADERS=true      take the client address from Cloudflare's headers (only behind that proxy)
 * Counters live in this process, so this is a first line of defense next to the limit rule in Cloudflare.
 */
type Limiters = { reads: RateLimiter | null; writes: RateLimiter | null; trustProxy: boolean };

let limiters: Limiters | undefined;

// read lazily: the environment of the running server, not of the build
function current(): Limiters {
  if (!limiters) {
    const reads = limitFromEnv(process.env.RATE_LIMIT_READS_PER_MINUTE);
    const writes = limitFromEnv(process.env.RATE_LIMIT_WRITES_PER_MINUTE);
    limiters = {
      reads: reads ? new RateLimiter(reads) : null,
      writes: writes ? new RateLimiter(writes) : null,
      trustProxy: process.env.TRUST_PROXY_HEADERS === 'true',
    };
  }
  return limiters;
}

export function proxy(request: NextRequest) {
  const { reads, writes, trustProxy } = current();
  const write = isWrite(request.method);
  const limiter = write ? writes : reads;
  if (!limiter) return NextResponse.next();

  const verdict = limiter.hit(clientKey(request.headers, trustProxy));
  if (verdict.allowed) return NextResponse.next();

  return new NextResponse('요청이 너무 많습니다. 잠시 후 다시 시도하세요.', {
    status: 429,
    headers: { 'Retry-After': String(verdict.retryAfterSeconds), 'Content-Type': 'text/plain; charset=utf-8' },
  });
}

// pages only: static files under /_next are not counted
export const config = { matcher: ['/admin/:path*'] };
