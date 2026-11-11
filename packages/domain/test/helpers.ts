/** Builds a FormData from plain entries; true becomes a checked checkbox ("on"), arrays and objects become JSON. */
export function form(entries: Record<string, string | boolean | unknown[] | undefined>): FormData {
  const fd = new FormData();
  for (const [k, v] of Object.entries(entries)) {
    if (v === undefined || v === false) continue;
    if (v === true) fd.set(k, 'on');
    else if (typeof v === 'string') fd.set(k, v);
    else fd.set(k, JSON.stringify(v));
  }
  return fd;
}
