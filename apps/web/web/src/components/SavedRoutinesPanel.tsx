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
}: SavedRoutinesPanelProps<T>) {
  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold text-heading">{title}</h3>
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
        <p className="text-muted text-sm">No routines yet.</p>
      ) : (
        <ul className="space-y-2">
          {routines.map((routine) => {
            const id = getId(routine);
            const isEditing = editingId === id;
            return (
              <li
                key={id}
                className={`card p-3 ${isEditing ? "ring-2 ring-[var(--erv-primary)]" : ""}`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-semibold truncate">
                      {getName(routine)}
                      {isEditing ? (
                        <span className="text-xs font-normal text-muted ml-2">editing</span>
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
