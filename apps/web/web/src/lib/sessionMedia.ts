import { useCallback, useEffect, useMemo, useState } from "react";
import { getAppData } from "./appDataCache";
import {
  MEDIA_LIBRARY_D_TAG,
  parseMediaLibraryManifest,
  type MediaLibraryItem,
  type MediaLibraryManifest,
} from "./mediaLibrary";

export const MEDIA_SOURCE_CARDIO_ROUTE = "cardio_route";
export const MEDIA_SOURCE_HEART_RATE_GRAPH = "heart_rate_graph";

export type SessionMediaIndex = Map<string, MediaLibraryItem>;

export function sessionMediaKey(source: string, localId: string): string {
  return `${source}:${localId}`;
}

export function buildSessionMediaIndex(manifest: MediaLibraryManifest): SessionMediaIndex {
  const index: SessionMediaIndex = new Map();
  for (const item of manifest.items) {
    index.set(sessionMediaKey(item.source, item.localId), item);
  }
  return index;
}

export function sessionMediaForId(
  index: SessionMediaIndex,
  sessionId: string,
): MediaLibraryItem[] {
  const items: MediaLibraryItem[] = [];
  const route = index.get(sessionMediaKey(MEDIA_SOURCE_CARDIO_ROUTE, sessionId));
  const hr = index.get(sessionMediaKey(MEDIA_SOURCE_HEART_RATE_GRAPH, sessionId));
  if (route) items.push(route);
  if (hr) items.push(hr);
  return items;
}

type SessionMediaState =
  | { kind: "loading" }
  | { kind: "ready"; index: SessionMediaIndex }
  | { kind: "error"; message: string };

export function useSessionMediaLibrary() {
  const [state, setState] = useState<SessionMediaState>({ kind: "loading" });

  const load = useCallback(async (force = false) => {
    try {
      const { records } = await getAppData({ force });
      const mediaRecord = records.find((r) => r.d_tag === MEDIA_LIBRARY_D_TAG);
      if (!mediaRecord?.plaintext) {
        setState({ kind: "ready", index: new Map() });
        return;
      }
      const manifest = parseMediaLibraryManifest(mediaRecord.plaintext);
      setState({ kind: "ready", index: buildSessionMediaIndex(manifest) });
    } catch (err) {
      setState({
        kind: "error",
        message: err instanceof Error ? err.message : "Could not load session media.",
      });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const index = useMemo(
    () => (state.kind === "ready" ? state.index : new Map<string, MediaLibraryItem>()),
    [state],
  );

  return {
    index,
    loading: state.kind === "loading",
    error: state.kind === "error" ? state.message : null,
    reload: load,
  };
}
