import { useState, type ReactNode } from "react";

type ReorderableListProps<T> = {
  items: T[];
  getKey: (item: T, index: number) => string;
  onReorder: (items: T[]) => void;
  renderItem: (item: T, index: number) => ReactNode;
  className?: string;
};

export function ReorderableList<T>({
  items,
  getKey,
  onReorder,
  renderItem,
  className = "space-y-1",
}: ReorderableListProps<T>) {
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [overIndex, setOverIndex] = useState<number | null>(null);

  const moveItem = (from: number, to: number) => {
    if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) {
      return;
    }
    const next = [...items];
    const [removed] = next.splice(from, 1);
    next.splice(to, 0, removed!);
    onReorder(next);
  };

  return (
    <ol className={className}>
      {items.map((item, index) => {
        const isDragging = dragIndex === index;
        const isOver = overIndex === index && dragIndex !== null && dragIndex !== index;
        return (
          <li
            key={getKey(item, index)}
            draggable
            onDragStart={() => setDragIndex(index)}
            onDragEnd={() => {
              setDragIndex(null);
              setOverIndex(null);
            }}
            onDragOver={(e) => {
              e.preventDefault();
              setOverIndex(index);
            }}
            onDragLeave={() => {
              if (overIndex === index) setOverIndex(null);
            }}
            onDrop={(e) => {
              e.preventDefault();
              if (dragIndex != null) moveItem(dragIndex, index);
              setDragIndex(null);
              setOverIndex(null);
            }}
            className={`flex items-center gap-2 rounded-card border px-3 py-2 bg-[var(--erv-input-bg)] text-sm transition-opacity ${
              isDragging ? "opacity-50 border-dashed" : "border-outline/30"
            } ${isOver ? "border-[var(--erv-primary)]" : ""}`}
          >
            <span
              className="cursor-grab active:cursor-grabbing text-muted select-none px-0.5"
              title="Drag to reorder"
              aria-hidden
            >
              ⠿
            </span>
            {renderItem(item, index)}
          </li>
        );
      })}
    </ol>
  );
}
