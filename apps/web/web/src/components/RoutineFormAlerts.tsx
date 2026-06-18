type RoutineFormAlertsProps = {
  error?: string | null;
  formError?: string | null;
  success?: string | null;
  lastEventId?: string | null;
  globalError?: string | null;
};

export function RoutineFormAlerts({
  error,
  formError,
  success,
  lastEventId,
  globalError,
}: RoutineFormAlertsProps) {
  return (
    <>
      {globalError ?? error ? (
        <p className="text-sm text-error card p-3" role="alert">
          {globalError ?? error}
        </p>
      ) : null}
      {success ? (
        <p
          className="text-sm card p-3 border-l-4 border-[var(--erv-success)]"
          role="status"
        >
          {success}
          {lastEventId ? (
            <span className="block text-xs text-muted mt-1 font-mono">
              event {lastEventId.slice(0, 16)}…
            </span>
          ) : null}
        </p>
      ) : null}
      {formError ? (
        <p className="text-sm text-error" role="alert">
          {formError}
        </p>
      ) : null}
    </>
  );
}
