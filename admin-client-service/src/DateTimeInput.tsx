import { useLayoutEffect, useRef, type KeyboardEvent, type MouseEvent } from 'react';

const format = 'YY-MM-DD HH:mm:ss';
const pattern = '[0-9]{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01]) (?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]';
const dateTimePattern = new RegExp(`^${pattern}$`);
const segments = [
  { start: 0, min: 0, max: 99 },
  { start: 3, min: 1, max: 12 },
  { start: 6, min: 1, max: 31 },
  { start: 9, min: 0, max: 23 },
  { start: 12, min: 0, max: 59 },
  { start: 15, min: 0, max: 59 },
];

function daysInMonth(year: number, month: number) {
  return new Date(Date.UTC(2000 + year, month, 0)).getUTCDate();
}

function readParts(value: string) {
  if (!dateTimePattern.test(value)) return null;
  const parts = value.split(/[- :]/).map(Number);
  return parts[2] <= daysInMonth(parts[0], parts[1]) ? parts : null;
}

function segmentAtCaret(input: HTMLInputElement) {
  const caret = input.selectionStart ?? 0;
  return segments.findIndex(segment => caret <= segment.start + 2);
}

function selectSegment(input: HTMLInputElement, index: number) {
  const { start } = segments[index];
  input.setSelectionRange(start, start + 2);
}

export function isValidDateTime(value: string) {
  return readParts(value) !== null;
}

export default function DateTimeInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const inputRef = useRef<HTMLInputElement>(null);
  const nextSelection = useRef<number | null>(null);

  useLayoutEffect(() => {
    const index = nextSelection.current;
    if (index === null) return;
    nextSelection.current = null;
    const input = inputRef.current;
    if (input && input === document.activeElement) selectSegment(input, index);
  }, [value]);

  function selectClickedSegment(event: MouseEvent<HTMLInputElement>) {
    const input = event.currentTarget;
    if (event.detail > 1 || event.shiftKey || input.selectionStart !== input.selectionEnd || !isValidDateTime(value)) return;
    const index = segmentAtCaret(input);
    if (index >= 0) selectSegment(input, index);
  }

  function handleArrowKey(event: KeyboardEvent<HTMLInputElement>) {
    const horizontal = event.key === 'ArrowLeft' || event.key === 'ArrowRight';
    const vertical = event.key === 'ArrowUp' || event.key === 'ArrowDown';
    if ((!horizontal && !vertical) || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
    const parts = readParts(value);
    if (!parts) return;

    const input = event.currentTarget;
    const index = segmentAtCaret(input);
    if (index < 0) return;
    event.preventDefault();
    if (horizontal) {
      const offset = event.key === 'ArrowRight' ? 1 : -1;
      selectSegment(input, Math.min(Math.max(index + offset, 0), segments.length - 1));
      return;
    }

    const segment = segments[index];
    const max = index === 2 ? daysInMonth(parts[0], parts[1]) : segment.max;
    const amount = event.key === 'ArrowUp' ? 1 : -1;
    const range = max - segment.min + 1;
    parts[index] = segment.min + (parts[index] - segment.min + amount + range) % range;
    parts[2] = Math.min(parts[2], daysInMonth(parts[0], parts[1]));

    const [year, month, day, hour, minute, second] = parts.map(part => String(part).padStart(2, '0'));
    nextSelection.current = index;
    onChange(`${year}-${month}-${day} ${hour}:${minute}:${second}`);
  }

  return <input ref={inputRef} type="text" placeholder={format} title={`${format} · ←→로 이동, ↑↓로 값 조정`}
    pattern={pattern} maxLength={17} autoComplete="off" spellCheck={false} value={value}
    onChange={event => onChange(event.target.value.replace(/[Tt]/g, ' '))}
    onClick={selectClickedSegment} onKeyDown={handleArrowKey} />;
}
