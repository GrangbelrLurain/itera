'use client';

import { cn } from '@itera/ui';
import { useEffect, useState, useSyncExternalStore } from 'react';
import {
  MAC_CONTROLS_INSET,
  readShell,
  type ShellInfo,
  setCustomChrome,
  TITLEBAR_HEIGHT,
  windowControls,
} from './shell-bridge';

const noSubscription = () => () => {};
const serverSnapshot = () => null;

type Chrome = 'pending' | 'custom' | 'native';

/**
 * Title bar of the desktop shell. It draws only inside the shell (nothing in a normal browser): the shell hides its
 * native frame while this bar is on the page, and keeps the native frame if the shell does not allow it.
 * macOS keeps its own traffic lights, the bar only leaves room for them.
 */
export function ShellTitleBar() {
  const shell = useSyncExternalStore(noSubscription, readShell, serverSnapshot);
  const [chrome, setChrome] = useState<Chrome>('pending');

  useEffect(() => {
    if (!shell) return;
    let active = true;

    const apply = async () => {
      try {
        if (shell.platform !== 'macos') await setCustomChrome(true);
        if (active) setChrome('custom');
      } catch {
        if (active) setChrome('native'); // the shell refused: its own frame stays
      }
    };
    void apply();

    return () => {
      active = false;
      if (shell.platform !== 'macos') void setCustomChrome(false).catch(() => {});
    };
  }, [shell]);

  useEffect(() => {
    if (chrome !== 'custom') return;
    const root = document.documentElement;
    root.style.setProperty('--shell-titlebar-h', `${TITLEBAR_HEIGHT}px`);
    return () => {
      root.style.removeProperty('--shell-titlebar-h');
    };
  }, [chrome]);

  if (!shell || chrome !== 'custom') return null;
  return <Bar shell={shell} />;
}

function Bar({ shell }: { shell: ShellInfo }) {
  const mac = shell.platform === 'macos';
  return (
    <div
      data-tauri-drag-region
      className="fixed inset-x-0 top-0 z-50 flex select-none items-center bg-primary-strong text-[13px] text-on-primary"
      style={{ height: TITLEBAR_HEIGHT, paddingLeft: mac ? MAC_CONTROLS_INSET : 12 }}
    >
      {/* pointer-events-none: a press on the text reaches the bar itself, which is what starts the drag */}
      <span className="pointer-events-none font-semibold tracking-wide">Itera Enterprise Workstation</span>
      {!mac && <WindowButtons />}
    </div>
  );
}

function WindowButtons() {
  const [maximized, setMaximized] = useState(false);

  useEffect(() => {
    let active = true;
    const sync = () => {
      void windowControls()
        .then((w) => w.isMaximized())
        .then((value) => active && setMaximized(value))
        .catch(() => {});
    };
    sync();
    window.addEventListener('resize', sync);
    return () => {
      active = false;
      window.removeEventListener('resize', sync);
    };
  }, []);

  const run = (action: (w: Awaited<ReturnType<typeof windowControls>>) => Promise<unknown>) => () => {
    void windowControls()
      .then(action)
      .catch(() => {});
  };

  const base = 'grid h-full w-[46px] place-items-center text-on-primary outline-none';
  return (
    <div className="ml-auto flex h-full">
      <button
        type="button"
        aria-label="최소화"
        className={cn(base, 'hover:bg-white/15')}
        onClick={run((w) => w.minimize())}
      >
        <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
          <path d="M0 5h10" stroke="currentColor" strokeWidth="1" />
        </svg>
      </button>
      <button
        type="button"
        aria-label={maximized ? '이전 크기로' : '최대화'}
        className={cn(base, 'hover:bg-white/15')}
        onClick={run((w) => w.toggleMaximize())}
      >
        {maximized ? (
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" aria-hidden="true">
            <path d="M2.5 2.5V0.5h7v7h-2M0.5 2.5h7v7h-7z" stroke="currentColor" strokeWidth="1" />
          </svg>
        ) : (
          <svg width="10" height="10" viewBox="0 0 10 10" fill="none" aria-hidden="true">
            <rect x="0.5" y="0.5" width="9" height="9" stroke="currentColor" strokeWidth="1" />
          </svg>
        )}
      </button>
      <button
        type="button"
        aria-label="닫기"
        className={cn(base, 'hover:bg-[#d6455d]')}
        onClick={run((w) => w.close())}
      >
        <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true">
          <path d="M0 0l10 10M10 0L0 10" stroke="currentColor" strokeWidth="1" />
        </svg>
      </button>
    </div>
  );
}
