import { useCallback, useEffect, useMemo, useState } from "react";
import { SectionHeader } from "../components/FieldLabel";
import { api, relayHost } from "../lib/api";
import {
  fetchAndDecryptMediaItem,
  MEDIA_LIBRARY_D_TAG,
  mediaSourceLabel,
  parseMediaLibraryManifest,
  type MediaLibraryItem,
  type MediaLibraryManifest,
} from "../lib/mediaLibrary";

type GalleryState =
  | { kind: "loading" }
  | { kind: "empty" }
  | { kind: "ready"; manifest: MediaLibraryManifest }
  | { kind: "error"; message: string };

type PreviewState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "ready"; url: string }
  | { kind: "error"; message: string };

export function MediaGalleryTab() {
  const [state, setState] = useState<GalleryState>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    try {
      const { records } = await api.listAppData();
      const mediaRecord = records.find((r) => r.d_tag === MEDIA_LIBRARY_D_TAG);
      if (!mediaRecord?.plaintext) {
        setState({ kind: "empty" });
        return;
      }
      setState({
        kind: "ready",
        manifest: parseMediaLibraryManifest(mediaRecord.plaintext),
      });
    } catch (err) {
      setState({
        kind: "error",
        message:
          err instanceof Error ? err.message : "Could not load media library.",
      });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const manifest = state.kind === "ready" ? state.manifest : null;
  const sourceCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const item of manifest?.items ?? []) {
      counts.set(item.source, (counts.get(item.source) ?? 0) + 1);
    }
    return Array.from(counts.entries());
  }, [manifest]);

  return (
    <div className="mx-auto w-full max-w-6xl space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold text-heading">Media Gallery</h2>
          <p className="text-sm text-muted mt-1">
            Decrypt Blossom-backed ERV media from your encrypted media manifest.
          </p>
        </div>
        <button type="button" className="btn-ghost" onClick={() => void load()}>
          Refresh
        </button>
      </div>

      {state.kind === "loading" ? (
        <section className="card p-5 text-sm text-muted">
          Loading encrypted media manifest…
        </section>
      ) : null}

      {state.kind === "error" ? (
        <section className="card p-5 text-sm text-error">{state.message}</section>
      ) : null}

      {state.kind === "empty" ? (
        <section className="card p-5 space-y-2">
          <SectionHeader>No Media Manifest Yet</SectionHeader>
          <p className="text-sm text-muted">
            Back up Body Tracker progress photos from Android first. ERV will
            publish `erv/media/library`, then this gallery can decrypt and show
            the backed-up images.
          </p>
        </section>
      ) : null}

      {manifest ? (
        <>
          <section className="grid gap-3 md:grid-cols-3">
            <div className="metric-card">
              <p className="text-xs text-muted">Backed-up items</p>
              <p className="mt-1 text-2xl font-semibold text-heading">
                {manifest.items.length}
              </p>
            </div>
            <div className="metric-card">
              <p className="text-xs text-muted">Updated</p>
              <p className="mt-1 text-lg font-semibold text-heading">
                {manifest.updatedAtEpochSeconds
                  ? new Date(
                      manifest.updatedAtEpochSeconds * 1000,
                    ).toLocaleString()
                  : "Unknown"}
              </p>
            </div>
            <div className="metric-card">
              <p className="text-xs text-muted">Sources</p>
              <p className="mt-1 text-sm text-heading">
                {sourceCounts.length
                  ? sourceCounts
                      .map(([source, count]) => `${mediaSourceLabel(source)} (${count})`)
                      .join(", ")
                  : "None"}
              </p>
            </div>
          </section>

          {manifest.items.length === 0 ? (
            <section className="card p-5 text-sm text-muted">
              The media manifest exists but does not contain any items yet.
            </section>
          ) : (
            <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {manifest.items.map((item) => (
                <MediaTile key={item.id} item={item} />
              ))}
            </section>
          )}
        </>
      ) : null}
    </div>
  );
}

function MediaTile({ item }: { item: MediaLibraryItem }) {
  const [preview, setPreview] = useState<PreviewState>({ kind: "idle" });

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
          message:
            err instanceof Error ? err.message : "Could not decrypt media.",
        });
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [item]);

  return (
    <article className="card overflow-hidden">
      <div className="aspect-[4/3] bg-[var(--erv-surface-muted)] flex items-center justify-center">
        {preview.kind === "ready" ? (
          <img
            src={preview.url}
            alt=""
            className="h-full w-full object-cover"
            loading="lazy"
          />
        ) : preview.kind === "error" ? (
          <div className="p-4 text-center text-xs text-error">
            {preview.message}
          </div>
        ) : (
          <div className="text-xs text-muted">Decrypting…</div>
        )}
      </div>
      <div className="p-4 space-y-2">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="font-semibold text-heading">
              {mediaSourceLabel(item.source)}
            </p>
            <p className="text-xs text-muted">{item.date ?? "Undated"}</p>
          </div>
          <span className="rounded-full bg-[var(--erv-input-bg)] px-2 py-1 text-xs text-muted">
            {formatBytes(item.sizeBytes)}
          </span>
        </div>
        <p className="text-xs font-mono text-muted truncate" title={item.blobUrl}>
          {relayHost(item.blobUrl)}
        </p>
        <p className="text-[11px] font-mono text-muted truncate" title={item.sha256}>
          sha256 {item.sha256}
        </p>
      </div>
    </article>
  );
}

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
}
