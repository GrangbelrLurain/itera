import type { Metadata } from 'next';
import { ShellTitleBar } from '@/features/shell/ShellTitleBar';
import './globals.css';

export const metadata: Metadata = {
  title: 'Itera',
  description: 'Itera admin',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <ShellTitleBar />
        {children}
      </body>
    </html>
  );
}
