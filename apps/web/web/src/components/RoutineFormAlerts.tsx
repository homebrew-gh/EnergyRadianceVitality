type RoutineFormAlertsProps = {
  error?: string | null;
  formError?: string | null;
  success?: string | null;
  /** Accepted for older callers; relay event ids now live in Settings → Advanced. */
  lastEventId?: string | null;
  globalError?: string | null;
};

export function RoutineFormAlerts({
  error,
  formError,
  success,
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
