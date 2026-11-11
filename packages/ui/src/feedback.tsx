import { cva } from 'class-variance-authority';
import type { CSSProperties, ReactNode } from 'react';
import { cn } from './cn';
import type { FormState } from './form';

/** Semantic color of a badge (the domain decides which status gets which tone). */
export type Tone = 'success' | 'warning' | 'muted' | 'neutral';

const badge = cva('inline-block rounded-full border px-2.5 py-0.5 text-xs', {
  variants: {
    tone: {
      success: 'border-success bg-success-soft text-success-fg',
      warning: 'border-warning bg-warning-soft text-warning-fg',
      muted: 'border-danger bg-danger-soft text-danger',
      neutral: 'border-line bg-surface text-fg-muted',
    },
  },
});

export function Badge({ tone, children, style }: { tone: Tone; children: ReactNode; style?: CSSProperties }) {
  return (
    <span className={badge({ tone })} style={style}>
      {children}
    </span>
  );
}

const banner = cva('my-3 rounded-sm border px-3.5 py-2.5 text-sm', {
  variants: {
    kind: {
      success: 'border-success bg-success-soft text-success-fg',
      error: 'border-danger bg-danger-soft text-danger',
    },
  },
});

export function Banner({
  kind,
  children,
  className,
}: {
  kind: 'success' | 'error';
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn(banner({ kind }), className)} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  );
}

/** Success / error banner of a form action. */
export function FormMessage({ state }: { state: FormState }) {
  if (state.error) return <Banner kind="error">{state.error}</Banner>;
  if (state.message) return <Banner kind="success">{state.message}</Banner>;
  return null;
}

/** Small secondary text (a span unless `as` says otherwise). */
export function Muted({
  as: Tag = 'span',
  className,
  children,
}: {
  as?: 'span' | 'p' | 'div';
  className?: string;
  children: ReactNode;
}) {
  return <Tag className={cn('text-[13px] text-fg-muted', className)}>{children}</Tag>;
}
