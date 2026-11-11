import type { ComponentProps, ComponentPropsWithoutRef, ElementType, ReactNode } from 'react';
import { cn } from './cn';

/**
 * A panel. With a `title` it gets a tinted header and a padded body whose children are spaced out;
 * without one it is a plain padded box. `tone="filter"` tints the whole panel (search bars).
 */
export function Card({
  title,
  tone,
  className,
  children,
}: {
  title?: ReactNode;
  tone?: 'filter';
  className?: string;
  children: ReactNode;
}) {
  return (
    <section
      className={cn(
        'mb-4 overflow-x-auto overflow-y-hidden rounded-md border border-line bg-surface',
        tone === 'filter' && 'border-primary-softer bg-primary-soft',
        className,
      )}
    >
      {title ? (
        <>
          <h2 className="border-primary-softer border-b bg-primary-soft px-5 py-2.5 font-bold text-[15px]">{title}</h2>
          <div className="px-5 py-4 [&>*+*]:mt-4">{children}</div>
        </>
      ) : (
        <div className={cn(tone === 'filter' ? 'px-5 py-4' : 'p-5')}>{children}</div>
      )}
    </section>
  );
}

/**
 * A data table. Style the cells through the table: plain th/td/tr children work.
 * Cells are left-aligned (theme base layer); right-align numbers with `className="text-right tabular-nums"`.
 */
export function Table({ className, ...props }: ComponentProps<'table'>) {
  return (
    <table
      className={cn(
        'w-full border-collapse text-sm',
        '[&_td]:border-line [&_td]:border-b [&_td]:px-2.5 [&_td]:py-2',
        '[&_th]:border-line [&_th]:border-b [&_th]:bg-surface-subtle [&_th]:px-2.5 [&_th]:py-2 [&_th]:font-semibold [&_th]:text-[13px] [&_th]:text-fg-muted',
        '[&_tbody_tr:hover]:bg-surface-subtle',
        '[&_td_a]:font-semibold [&_td_a]:text-primary-strong [&_td_a]:no-underline [&_td_a:hover]:underline',
        className,
      )}
      {...props}
    />
  );
}

/** Term / description pairs in a responsive grid (<dt>, <dd> children). */
export function DescriptionList({ className, ...props }: ComponentProps<'dl'>) {
  return (
    <dl
      className={cn(
        'grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-x-6 gap-y-3.5',
        '[&_dt]:text-fg-muted [&_dt]:text-xs [&_dd]:mt-0.5 [&_dd]:ml-0 [&_dd]:text-sm',
        className,
      )}
      {...props}
    />
  );
}

export function PageTitle({ className, ...props }: ComponentProps<'h1'>) {
  return <h1 className={cn('mb-1 font-bold text-[22px]', className)} {...props} />;
}

/** Row of buttons / fields next to each other. */
export function Cluster({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex flex-wrap items-center gap-2', className)} {...props} />;
}

type StatProps<T extends ElementType> = {
  /** Render as another element, e.g. a link: <Stat as={Link} href="/x" ... />. */
  as?: T;
  label: ReactNode;
  value: ReactNode;
  emphasis?: boolean;
} & Omit<ComponentPropsWithoutRef<T>, 'as' | 'children'>;

/** A number with a label above, as shown on the dashboard. */
export function Stat<T extends ElementType = 'div'>({ as, label, value, emphasis, className, ...props }: StatProps<T>) {
  const Tag: ElementType = as ?? 'div';
  const linked = as !== undefined && as !== 'div';
  return (
    <Tag
      className={cn(
        'block rounded-md border border-line bg-surface px-4 py-3.5 text-fg no-underline',
        linked && 'hover:border-primary',
        className,
      )}
      {...props}
    >
      <span className="block text-fg-muted text-xs">{label}</span>
      <strong className={cn('mt-1 block text-2xl tabular-nums', emphasis && 'text-primary-strong')}>{value}</strong>
    </Tag>
  );
}

/** Grid of Stat cards. */
export function StatGrid({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div className={cn('my-4 grid grid-cols-[repeat(auto-fill,minmax(190px,1fr))] gap-3', className)} {...props} />
  );
}

/** Page navigation under a list: a summary on the left, previous / next on the right. */
export function Pager({ summary, prev, next }: { summary: ReactNode; prev: ReactNode; next: ReactNode }) {
  return (
    <div className="mt-2.5 flex items-center justify-between text-sm">
      <span className="text-[13px] text-fg-muted">{summary}</span>
      <span className="flex gap-3">
        {prev}
        {next}
      </span>
    </div>
  );
}

/** A previous / next entry that cannot be followed. */
export function PagerOff({ children }: { children: ReactNode }) {
  return <span className="text-fg-dim">{children}</span>;
}
