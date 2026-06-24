type SavedRoutinesPanelProps<T> = {
  title?: string;
  loading: boolean;
  routines: T[];
  editingId?: string | null;
  onReload: () => void;
  getId: (routine: T) => string;
  getName: (routine: T) => string;
  getDetail: (routine: T) => string;
  onEdit: (routine: T) => void;
  onDelete: (routine: T) => void;
  onDuplicate?: (routine: T) => void;
  onDuplicateWithProgress?: (routine: T) => void;
};

export function SavedRoutinesPanel<T>({
  title = "On relay now",
  loading,
  routines,
  editingId,
  onReload,
  getId,
  getName,
  getDetail,
  onEdit,
  onDelete,
  onDuplicate,
  onDuplicateWithProgress,
}: SavedRoutinesPanelProps<T>) {
  return (
    <section className="card p-4 space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold text-heading">{title}</h3>
          <p className="text-xs text-muted">
            Reuse, progress, or edit a session already published to the relay.
          </p>
        </div>
        <button
          type="button"
          className="btn-ghost text-sm"
          onClick={onReload}
          disabled={loading}
        >
          Refresh
        </button>
      </div>
      {loading ? (
        <p className="text-muted text-sm">Loading…</p>
      ) : routines.length === 0 ? (
        <p className="rounded-card border border-dashed border-[var(--erv-outline-variant)] p-4 text-sm text-muted">
          No routines yet. Compose a session below and publish it to start your library.
        </p>
      ) : (
        <ul className="space-y-2">
          {routines.map((routine) => {
            const id = getId(routine);
            const isEditing = editingId === id;
            return (
              <li
                key={id}
                className={`rounded-card border bg-[var(--erv-input-bg)] p-3 transition hover:shadow-sm ${
                  isEditing
                    ? "border-[var(--erv-primary)] ring-2 ring-[var(--erv-primary)]/25"
                    : "border-[var(--erv-outline-variant)]"
                }`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold text-heading truncate">
                      {getName(routine)}
                      {isEditing ? (
                        <span className="sun-chip ml-2 py-0.5">Editing</span>
                      ) : null}
                    </p>
                    <p className="text-sm text-muted mt-1">{getDetail(routine)}</p>
                  </div>
                  <div className="flex gap-1 shrink-0">
                    <button
                      type="button"
                      className="btn-ghost text-xs py-1 px-2"
                      onClick={() => onEdit(routine)}
                    >
                      Edit
                    </button>
                    {onDuplicate ? (
                      <button
                        type="button"
                        className="btn-ghost text-xs py-1 px-2"
                        onClick={() => onDuplicate(routine)}
                      >
                        Duplicate
                      </button>
                    ) : null}
                    {onDuplicateWithProgress ? (
                      <button
                        type="button"
                        className="btn-ghost text-xs py-1 px-2"
                        title="Duplicate and bump loads from last week or baseline"
                        onClick={() => onDuplicateWithProgress(routine)}
                      >
                        + Progress
                      </button>
                    ) : null}
                    <button
                      type="button"
                      className="btn-ghost text-xs py-1 px-2"
                      onClick={() => onDelete(routine)}
                    >
                      Delete
                    </button>
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
