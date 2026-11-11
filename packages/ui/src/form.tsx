import type { ComponentProps, ReactNode } from 'react';
import { cn } from './cn';

/** Result of a form action: shown by the form; `values` lets the form keep what the user typed after an error. */
export type FormState = {
  error?: string;
  message?: string;
  values?: Record<string, string>;
};

export const emptyFormState: FormState = {};

const control =
  'h-[34px] w-full min-w-0 rounded-sm border border-line bg-surface px-2.5 font-[inherit] text-sm text-fg outline-primary focus:outline-2 -outline-offset-1';

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input className={cn(control, props.type === 'number' && 'text-right', className)} {...props} />;
}

export function Select({ className, ...props }: ComponentProps<'select'>) {
  return <select className={cn(control, className)} {...props} />;
}

export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return <textarea className={cn(control, 'h-auto py-2 leading-normal', className)} {...props} />;
}

/** A label with its control underneath. */
export function Field({ label, children, className }: { label: ReactNode; children: ReactNode; className?: string }) {
  return (
    // biome-ignore lint/a11y/noLabelWithoutControl: the control is passed as children and sits inside the label
    <label className={cn('flex min-w-0 flex-col gap-1 text-xs text-fg-muted', className)}>
      <span>{label}</span>
      {children}
    </label>
  );
}

/** A group of related controls (checkboxes, chips) with a legend. */
export function FieldGroup({
  legend,
  children,
  className,
}: {
  legend: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <fieldset className={cn('m-0 flex min-w-0 flex-col gap-1 border-0 p-0 text-xs text-fg-muted', className)}>
      <legend className="mb-1 p-0">{legend}</legend>
      {children}
    </fieldset>
  );
}

/** A checkbox with its text next to it. */
export function Check({ children, className, ...props }: ComponentProps<'input'> & { children: ReactNode }) {
  return (
    <label className={cn('inline-flex cursor-pointer items-center gap-1.5 text-sm text-fg', className)}>
      <input type="checkbox" className="size-4 accent-primary" {...props} />
      {children}
    </label>
  );
}

/** A toggle shown as a small button (e.g. weekdays); the checkbox itself is visually hidden. */
export function Chip({ children, className, ...props }: ComponentProps<'input'> & { children: ReactNode }) {
  return (
    <label className={cn('cursor-pointer', className)}>
      <input type="checkbox" className="peer sr-only" {...props} />
      <span className="inline-block min-w-8 rounded-sm border border-primary bg-surface py-1.5 text-center text-[13px] text-primary-strong peer-checked:bg-primary peer-checked:text-on-primary peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-primary-strong">
        {children}
      </span>
    </label>
  );
}

/** Responsive grid of fields; give a child `col-span-2` to make it wide. */
export function FormGrid({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      className={cn('grid grid-cols-[repeat(auto-fill,minmax(200px,1fr))] items-end gap-x-5 gap-y-3', className)}
      {...props}
    />
  );
}
