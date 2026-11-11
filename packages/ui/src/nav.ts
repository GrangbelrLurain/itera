import { cn } from './cn';

/** Class of a link that reads as a plain link (use on next/link or <a>). */
export const linkClass = 'text-primary-strong no-underline hover:underline';

/** Class of a tab link in a tab bar (wrap the tabs in <TabBar>). */
export function tabClass(active: boolean) {
  return cn(
    'whitespace-nowrap border-transparent border-b-2 px-4 py-2 text-fg-muted text-sm no-underline hover:text-primary-strong',
    active && 'border-primary font-bold text-primary-strong',
  );
}

/** Class of a side navigation link. */
export const navLinkClass =
  'block px-5 py-2.5 text-fg text-sm no-underline hover:bg-primary-soft hover:text-primary-strong max-md:whitespace-nowrap';

/** Class of the "back to the list" link above a page. */
export const backLinkClass = 'mb-3 inline-block text-primary-strong text-sm no-underline hover:underline';

/** Class of a link inside a pager. */
export const pagerLinkClass = 'text-primary-strong no-underline hover:underline';
