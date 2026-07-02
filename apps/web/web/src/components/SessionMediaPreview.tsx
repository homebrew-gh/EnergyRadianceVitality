import { useEffect, useState } from "react";
import {
  fetchAndDecryptMediaItem,
  mediaSourceLabel,
  type MediaLibraryItem,
} from "../lib/mediaLibrary";

type PreviewState =
  | { kind: "loading" }
  | { kind: "ready"; url: string }
  | { kind: "error"; message: string };

export function SessionMediaPreview({ item }: { item: MediaLibraryItem }) {
  const [preview, setPreview] = useState<PreviewState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;
    let objectUrl: string | null = null;
    setPreview({ kind: "loading" });
    fetchAndDecryptMediaItem(item)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setPreview({ kind: "ready", url: objectUrl });
      })
      .catch((err) => {
        if (cancelled) return;
        setPreview({
          kind: "error",
          message: err instanceof Error ? err.message : "Could not decrypt media.",
        });
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [item]);

  return (
    <figure className="overflow-hidden rounded-xl border border-[var(--erv-outline-variant)] bg-[var(--erv-surface-muted)]">
      <div className="aspect-[4/3] flex items-center justify-center">
        {preview.kind === "ready" ? (
          <img
            src={preview.url}
            alt=""
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : preview.kind === "error" ? (
          <p className="px-3 text-center text-xs text-error">{preview.message}</p>
        ) : (
          <p className="text-xs text-muted">Decrypting…</p>
        )}
      </div>
      <figcaption className="px-3 py-2 text-xs text-muted">
        {mediaSourceLabel(item.source)}
      </figcaption>
    </figure>
  );
}

export function SessionMediaGallery({
  items,
  emptyMessage,
}: {
  items: MediaLibraryItem[];
  emptyMessage?: string;
}) {
  if (items.length === 0) {
    return emptyMessage ? (
      <p className="text-xs text-muted">{emptyMessage}</p>
    ) : null;
  }
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {items.map((item) => (
        <SessionMediaPreview key={item.id} item={item} />
      ))}
    </div>
  );
}
