import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from './cn';

/** Also usable on links: <Link className={buttonVariants({ variant: 'outline' })}>. */
export const buttonVariants = cva(
  'inline-flex cursor-pointer items-center justify-center rounded-sm text-sm no-underline disabled:cursor-not-allowed',
  {
    variants: {
      variant: {
        primary:
          'border-0 bg-primary px-[22px] py-2.5 font-bold text-on-primary hover:bg-primary-strong disabled:bg-primary-softer',
        outline:
          'border border-primary bg-surface px-4 py-2 text-primary-strong hover:bg-primary-soft disabled:opacity-50',
        danger: 'border border-danger bg-surface px-4 py-2 text-danger hover:bg-danger-soft disabled:opacity-50',
        link: 'border-0 bg-transparent p-0 text-primary-strong hover:underline disabled:opacity-50',
        'link-danger': 'border-0 bg-transparent p-0 text-danger hover:underline disabled:opacity-50',
      },
      size: {
        md: '',
        sm: 'px-2.5 py-1 text-[13px]',
      },
      block: { true: 'w-full', false: '' },
    },
    compoundVariants: [{ variant: ['link', 'link-danger'], size: 'sm', className: 'p-0 text-sm' }],
    defaultVariants: { variant: 'primary', size: 'md', block: false },
  },
);

export type ButtonProps = ComponentProps<'button'> & VariantProps<typeof buttonVariants>;

export function Button({ className, variant, size, block, type = 'button', ...props }: ButtonProps) {
  return <button type={type} className={cn(buttonVariants({ variant, size, block }), className)} {...props} />;
}
