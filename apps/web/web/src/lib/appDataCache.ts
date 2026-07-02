import { api, type AppDataListResponse } from "./api";

/** Reuse relay snapshots for a few minutes unless forced or invalidated after a publish. */
export const APP_DATA_TTL_MS = 5 * 60 * 1000;

let cached: AppDataListResponse | null = null;
let cachedAt = 0;
let invalidated = false;
let inflight: Promise<AppDataListResponse> | null = null;

export function invalidateAppDataCache() {
  cached = null;
  cachedAt = 0;
  invalidated = true;
}

export function appDataCacheAgeMs(): number | null {
  if (!cached) return null;
  return Date.now() - cachedAt;
}

export async function getAppData(options?: {
  force?: boolean;
}): Promise<AppDataListResponse> {
  const force = options?.force ?? invalidated;
  const now = Date.now();

  if (force) {
    invalidated = false;
  } else if (cached && now - cachedAt < APP_DATA_TTL_MS) {
    return cached;
  }

  if (!force && inflight) {
    return inflight;
  }

  inflight = api
    .listAppData()
    .then((response) => {
      cached = response;
      cachedAt = Date.now();
      invalidated = false;
      inflight = null;
      return response;
    })
    .catch((error) => {
      inflight = null;
      throw error;
    });

  return inflight;
}
