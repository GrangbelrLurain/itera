/**
 * The desktop shell (apps/desktop-shell) tells the page that it lives in the shell by defining
 * `window.__ITERA_SHELL__` before any script runs. In a normal browser it does not exist and none of this runs.
 */
export type ShellInfo = {
  platform: 'windows' | 'macos' | 'linux';
  version: string;
};

declare global {
  interface Window {
    __ITERA_SHELL__?: ShellInfo;
  }
}

export function readShell(): ShellInfo | null {
  return typeof window === 'undefined' ? null : (window.__ITERA_SHELL__ ?? null);
}

/** Height of the title bar the page draws inside the shell. */
export const TITLEBAR_HEIGHT = 36;

/** Width the macOS traffic lights take on the left of the title bar. */
export const MAC_CONTROLS_INSET = 76;

/** Asks the shell to hide (or bring back) the native window frame. Rejects when the shell does not allow it. */
export async function setCustomChrome(enabled: boolean): Promise<void> {
  const { invoke } = await import('@tauri-apps/api/core');
  await invoke('use_custom_chrome', { enabled });
}

export async function windowControls() {
  const { getCurrentWindow } = await import('@tauri-apps/api/window');
  return getCurrentWindow();
}
