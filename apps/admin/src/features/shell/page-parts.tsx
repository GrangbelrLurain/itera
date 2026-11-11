import { Banner, Card, Muted } from '@itera/ui';

/** First value of a query parameter; blank counts as missing. */
export function first(v: string | string[] | undefined): string | null {
  const s = Array.isArray(v) ? v[0] : v;
  return s ? s : null;
}

export type SearchParams = Record<string, string | string[] | undefined>;

/** Shown instead of a page whose data could not be loaded. */
export function LoadError({ message }: { message: string }) {
  return (
    <Card title="불러오지 못했습니다">
      <Muted as="p">{message}</Muted>
    </Card>
  );
}

/** The ?msg= and ?error= banners that redirecting actions leave on a page. */
export function Notices({ msg, error }: { msg?: string | null; error?: string | null }) {
  return (
    <>
      {msg && <Banner kind="success">{msg}</Banner>}
      {error && <Banner kind="error">{error}</Banner>}
    </>
  );
}
