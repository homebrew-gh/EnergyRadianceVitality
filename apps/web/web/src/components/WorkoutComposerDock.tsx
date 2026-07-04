type WorkoutComposerDockProps = {
  visible: boolean;
  formId: string;
  saving: boolean;
  editing: boolean;
  /** Short context shown above the button (e.g. workout name). */
  draftLabel?: string;
  segmentCount: number;
  itemCount: number;
  onCancelEdit?: () => void;
};

export function WorkoutComposerDock({
  visible,
  formId,
  saving,
  editing,
  draftLabel,
  segmentCount,
  itemCount,
  onCancelEdit,
}: WorkoutComposerDockProps) {
  if (!visible) return null;

  const publishLabel = saving
    ? "Publishing…"
    : editing
      ? "Publish update"
      : "Publish to relay";

  return (
    <div
      className="pointer-events-none fixed inset-x-3 bottom-4 z-30 lg:relative lg:inset-auto lg:bottom-auto lg:z-auto lg:w-full"
      role="complementary"
      aria-label="Workout publish actions"
    >
      <div className="pointer-events-auto glass-panel rounded-[22px] border border-[var(--erv-outline-variant)]/90 p-3 shadow-[var(--erv-shadow-card)]">
        <div className="mb-2 space-y-0.5">
          <p className="text-[11px] font-semibold text-[var(--erv-primary)]">
            {editing ? "Editing" : "Draft"}
          </p>
          <p
            className="truncate text-sm font-semibold text-heading"
            title={draftLabel || "Untitled workout"}
          >
            {draftLabel?.trim() || "Untitled workout"}
          </p>
          <p className="text-[11px] text-muted">
            {segmentCount} segment{segmentCount === 1 ? "" : "s"} · {itemCount} item
            {itemCount === 1 ? "" : "s"}
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <button
            type="submit"
            form={formId}
            className="btn-primary w-full text-sm shadow-md"
            disabled={saving}
          >
            {publishLabel}
          </button>
          {editing && onCancelEdit ? (
            <button
              type="button"
              className="btn-ghost w-full text-sm"
              onClick={onCancelEdit}
              disabled={saving}
            >
              Cancel edit
            </button>
          ) : null}
        </div>
      </div>
    </div>
  );
}
