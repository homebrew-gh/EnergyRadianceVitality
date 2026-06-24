import { useEffect } from "react";

const LEAVE_MESSAGE =
  "You have unsaved equipment changes. Push updates to your relay before leaving, or your progress will be lost.";

function isInternalAppLink(href: string): boolean {
  if (!href || href.startsWith("http") || href.startsWith("mailto:")) return false;
  return href.startsWith("/");
}

/**
 * Warns when navigating away or closing the tab with unsaved equipment edits.
 */
export function useUnsavedChangesWarning(active: boolean, message = LEAVE_MESSAGE) {
  useEffect(() => {
    if (!active) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = message;
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [active, message]);

  useEffect(() => {
    if (!active) return;
    const onClickCapture = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const anchor = target.closest("a[href]");
      if (!anchor) return;
      const href = anchor.getAttribute("href");
      if (!href || !isInternalAppLink(href)) return;
      if (!window.confirm(message)) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    document.addEventListener("click", onClickCapture, true);
    return () => document.removeEventListener("click", onClickCapture, true);
  }, [active, message]);
}
