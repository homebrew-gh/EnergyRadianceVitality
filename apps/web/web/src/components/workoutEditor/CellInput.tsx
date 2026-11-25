import { useEffect, useRef, useState } from "react";
import clsx from "clsx";

/**
 * A spreadsheet-style cell: type freely, and the text is read when you leave the cell or press
 * Enter. Escape puts the old value back. Text the row cannot read is simply not applied, and the
 * cell shows the value as stored again.
 */
export function CellInput({
  value,
  onCommit,
  ariaLabel,
  placeholder,
  className,
  inputMode,
  title,
}: {
  value: string;
  onCommit: (text: string) => void;
  ariaLabel: string;
  placeholder?: string;
  className?: string;
  inputMode?: "numeric" | "decimal" | "text";
  title?: string;
}) {
  const [text, setText] = useState(value);
  const editing = useRef(false);
  /** Text already applied while this cell kept focus (Enter), so leaving does not apply it twice. */
  const applied = useRef<string | null>(null);
  const cancelled = useRef(false);

  useEffect(() => {
    if (!editing.current) setText(value);
  }, [value]);

  const commit = (current: string) => {
    if (current === value || current === applied.current) return;
    applied.current = current;
    onCommit(current);
  };

  return (
    <input
      className={clsx(
        "w-full rounded-lg border border-transparent bg-transparent px-2 py-1.5 text-sm tabular-nums",
        "hover:border-[var(--erv-outline-variant)] focus:border-[var(--erv-primary)] focus:bg-[var(--erv-input-bg)] focus:outline-none",
        className,
      )}
      value={text}
      placeholder={placeholder}
      aria-label={ariaLabel}
      title={title}
      inputMode={inputMode}
      autoComplete="off"
      onFocus={(e) => {
        editing.current = true;
        applied.current = null;
        cancelled.current = false;
        e.currentTarget.select();
      }}
      onChange={(e) => setText(e.target.value)}
      onBlur={(e) => {
        editing.current = false;
        if (!cancelled.current) commit(e.currentTarget.value);
        setText(value);
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          commit(e.currentTarget.value);
          e.currentTarget.select();
        } else if (e.key === "Escape") {
          e.preventDefault();
          cancelled.current = true;
          setText(value);
          e.currentTarget.blur();
        }
      }}
    />
  );
}
