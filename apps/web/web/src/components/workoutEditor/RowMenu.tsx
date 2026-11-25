import { useEffect, useLayoutEffect, useRef, useState } from "react";
import clsx from "clsx";

export type RowMenuAction = {
  label: string;
  onSelect: () => void;
  disabled?: boolean;
  danger?: boolean;
};

const MENU_WIDTH = 256;

/**
 * The "⋯" menu at the end of a table row. It opens in a fixed layer so the table's scrolling
 * box does not cut it off, and closes on a pick, a click elsewhere, Escape or scrolling.
 */
export function RowMenu({
  label,
  actions,
  buttonText,
  buttonClassName,
}: {
  label: string;
  actions: RowMenuAction[];
  /** Text for the button instead of "⋯", for menus outside a table row. */
  buttonText?: string;
  buttonClassName?: string;
}) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    if (!open) return;
    const button = buttonRef.current?.getBoundingClientRect();
    const menuHeight = menuRef.current?.offsetHeight ?? 0;
    if (!button) return;
    const below = button.bottom + 4;
    const top = below + menuHeight > window.innerHeight - 8 ? Math.max(8, button.top - menuHeight - 4) : below;
    setPosition({ top, left: Math.max(8, button.right - MENU_WIDTH) });
    menuRef.current?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Node;
      if (!menuRef.current?.contains(target) && !buttonRef.current?.contains(target)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.stopPropagation();
        setOpen(false);
        buttonRef.current?.focus();
      }
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const buttons = [...(menuRef.current?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)") ?? [])];
        const at = buttons.indexOf(document.activeElement as HTMLButtonElement);
        const next = event.key === "ArrowDown" ? (at + 1) % buttons.length : (at - 1 + buttons.length) % buttons.length;
        buttons[next]?.focus();
      }
    };
    const close = () => setOpen(false);
    document.addEventListener("mousedown", onPointer);
    document.addEventListener("keydown", onKey, true);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("mousedown", onPointer);
      document.removeEventListener("keydown", onKey, true);
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        tabIndex={buttonText ? undefined : -1}
        className={
          buttonClassName ??
          clsx(
            "rounded px-2 py-0.5 text-base leading-none hover:bg-[var(--erv-surface-variant)] hover:text-heading",
            open ? "bg-[var(--erv-surface-variant)] text-heading" : "text-muted",
          )
        }
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => {
          setPosition(null);
          setOpen((v) => !v);
        }}
      >
        {buttonText ?? "⋯"}
      </button>
      {open ? (
        <div
          ref={menuRef}
          role="menu"
          aria-label={label}
          className="fixed z-50 rounded-lg border border-[var(--erv-outline-variant)] bg-[var(--erv-surface)] py-1 text-left shadow-xl"
          style={{ width: MENU_WIDTH, top: position?.top ?? -9999, left: position?.left ?? -9999 }}
        >
          {actions.map((action) => (
            <button
              key={action.label}
              type="button"
              role="menuitem"
              disabled={action.disabled}
              className={clsx(
                "block w-full px-3 py-1.5 text-left text-sm hover:bg-[var(--erv-surface-variant)] focus:bg-[var(--erv-surface-variant)] focus:outline-none disabled:opacity-40 disabled:hover:bg-transparent",
                action.danger ? "text-[var(--erv-error)]" : "text-heading",
              )}
              onClick={() => {
                setOpen(false);
                action.onSelect();
              }}
            >
              {action.label}
            </button>
          ))}
        </div>
      ) : null}
    </>
  );
}
