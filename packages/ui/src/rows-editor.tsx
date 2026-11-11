'use client';

import { useState } from 'react';
import { Button } from './button';
import { cn } from './cn';
import { Chip, Input, Select, Textarea } from './form';

export type Column = {
  key: string;
  label: string;
  type: 'text' | 'number' | 'date' | 'select' | 'checkbox' | 'textarea' | 'chips';
  /** Choices of a select or of a chips column. */
  options?: readonly { value: string; label: string }[];
  placeholder?: string;
  /** CSS width of the cell (e.g. "90px"); other columns share the remaining space. */
  width?: string;
};

type Row = Record<string, unknown>;

/**
 * Editable list of rows. The whole list is serialized as JSON into a hidden input named `name`,
 * so a plain server-action form can submit it.
 */
export function RowsEditor({
  name,
  columns,
  initial,
  emptyRow,
  addLabel = '+ 행 추가',
  emptyText = '등록된 항목이 없습니다.',
  orderable = true,
}: {
  name: string;
  columns: Column[];
  initial: Row[];
  emptyRow: Row;
  addLabel?: string;
  emptyText?: string;
  orderable?: boolean;
}) {
  const [rows, setRows] = useState<Row[]>(initial);

  const update = (i: number, key: string, value: unknown) =>
    setRows((prev) => prev.map((r, idx) => (idx === i ? { ...r, [key]: value } : r)));
  const remove = (i: number) => setRows((prev) => prev.filter((_, idx) => idx !== i));
  const move = (i: number, delta: number) =>
    setRows((prev) => {
      const j = i + delta;
      if (j < 0 || j >= prev.length) return prev;
      const next = [...prev];
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });

  return (
    <div className="grid gap-2.5">
      <input type="hidden" name={name} value={JSON.stringify(rows)} readOnly />
      {rows.length === 0 && <p className="text-[13px] text-fg-muted">{emptyText}</p>}
      {rows.map((row, i) => (
        <div
          // biome-ignore lint/suspicious/noArrayIndexKey: rows are plain values without an id; they are edited in place
          key={i}
          className="flex flex-wrap items-end gap-x-3 gap-y-2 rounded-md border border-line bg-surface-subtle px-3 py-2.5"
        >
          {columns.map((c) => (
            // biome-ignore lint/a11y/noLabelWithoutControl: the control is rendered conditionally inside the label
            <label
              key={c.key}
              className="flex min-w-0 flex-[1_1_160px] flex-col gap-[3px] text-fg-muted text-xs"
              style={c.width ? { flex: `0 0 ${c.width}` } : undefined}
            >
              <span>{c.label}</span>
              {c.type === 'select' ? (
                <Select value={String(row[c.key] ?? '')} onChange={(e) => update(i, c.key, e.target.value)}>
                  {c.options?.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Select>
              ) : c.type === 'checkbox' ? (
                <input
                  type="checkbox"
                  className="my-2 size-[18px] accent-primary"
                  checked={Boolean(row[c.key])}
                  onChange={(e) => update(i, c.key, e.target.checked)}
                />
              ) : c.type === 'textarea' ? (
                <Textarea
                  rows={2}
                  value={String(row[c.key] ?? '')}
                  placeholder={c.placeholder}
                  onChange={(e) => update(i, c.key, e.target.value)}
                />
              ) : c.type === 'chips' ? (
                <span className="flex gap-1">
                  {c.options?.map((o) => {
                    const selected = Array.isArray(row[c.key]) && (row[c.key] as string[]).includes(o.value);
                    return (
                      <Chip
                        key={o.value}
                        checked={selected}
                        onChange={(e) => {
                          const current = Array.isArray(row[c.key]) ? (row[c.key] as string[]) : [];
                          update(
                            i,
                            c.key,
                            e.target.checked ? [...current, o.value] : current.filter((v) => v !== o.value),
                          );
                        }}
                      >
                        {o.label}
                      </Chip>
                    );
                  })}
                </span>
              ) : (
                <Input
                  type={c.type}
                  step={c.type === 'number' ? 'any' : undefined}
                  min={c.type === 'number' ? 0 : undefined}
                  value={row[c.key] === null || row[c.key] === undefined ? '' : String(row[c.key])}
                  placeholder={c.placeholder}
                  onChange={(e) => update(i, c.key, e.target.value)}
                />
              )}
            </label>
          ))}
          <span className={cn('ml-auto flex items-center gap-2')}>
            {orderable && (
              <>
                <Button variant="link" onClick={() => move(i, -1)} disabled={i === 0} aria-label="위로">
                  ↑
                </Button>
                <Button variant="link" onClick={() => move(i, 1)} disabled={i === rows.length - 1} aria-label="아래로">
                  ↓
                </Button>
              </>
            )}
            <Button variant="link-danger" onClick={() => remove(i)}>
              삭제
            </Button>
          </span>
        </div>
      ))}
      <Button
        variant="outline"
        className="justify-self-start"
        onClick={() => setRows((prev) => [...prev, { ...emptyRow }])}
      >
        {addLabel}
      </Button>
    </div>
  );
}
