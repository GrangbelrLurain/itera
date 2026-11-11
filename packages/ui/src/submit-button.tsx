'use client';

import type { ReactNode } from 'react';
import { useFormStatus } from 'react-dom';
import { Button } from './button';

/** Submit button that disables itself while the form's server action runs. */
export function SubmitButton({ children, pendingText = '저장 중…' }: { children: ReactNode; pendingText?: string }) {
  const { pending } = useFormStatus();
  return (
    <Button type="submit" disabled={pending}>
      {pending ? pendingText : children}
    </Button>
  );
}

/** Submit button that asks for confirmation first (the form itself is a server action form). */
export function ConfirmSubmit({
  children,
  message,
  variant = 'danger',
  size,
}: {
  children: ReactNode;
  message: string;
  variant?: 'primary' | 'outline' | 'danger';
  size?: 'md' | 'sm';
}) {
  return (
    <Button
      type="submit"
      variant={variant}
      size={size}
      onClick={(e) => {
        if (!window.confirm(message)) e.preventDefault();
      }}
    >
      {children}
    </Button>
  );
}
