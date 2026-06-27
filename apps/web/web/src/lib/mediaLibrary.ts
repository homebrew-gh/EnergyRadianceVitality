export const MEDIA_LIBRARY_D_TAG = "erv/media/library";

export type MediaBlobEncryption = {
  algorithm: "AES-256-GCM" | string;
  keyBase64: string;
  nonceBase64: string;
};

export type MediaLibraryItem = {
  id: string;
  source: string;
  localId: string;
  date?: string | null;
  blobUrl: string;
  blossomOrigin: string;
  sha256: string;
  encryptedSha256: string;
  sizeBytes: number;
  contentType: string;
  encryptedContentType?: string;
  encryption: MediaBlobEncryption;
  uploadedAtEpochSeconds?: number;
};

export type MediaLibraryManifest = {
  version: number;
  updatedAtEpochSeconds: number;
  items: MediaLibraryItem[];
};

export function parseMediaLibraryManifest(raw: string): MediaLibraryManifest {
  const parsed = JSON.parse(raw) as Partial<MediaLibraryManifest>;
  return {
    version: parsed.version ?? 1,
    updatedAtEpochSeconds: parsed.updatedAtEpochSeconds ?? 0,
    items: Array.isArray(parsed.items) ? parsed.items.filter(isMediaItem) : [],
  };
}

export function mediaSourceLabel(source: string): string {
  switch (source) {
    case "body_tracker":
      return "Body Tracker";
    case "cardio_route":
      return "Cardio Route";
    case "workout_route":
      return "Workout Route";
    default:
      return source.replace(/[_-]+/g, " ");
  }
}

export async function fetchAndDecryptMediaItem(
  item: MediaLibraryItem,
): Promise<Blob> {
  if (item.encryption.algorithm !== "AES-256-GCM") {
    throw new Error(`Unsupported encryption: ${item.encryption.algorithm}`);
  }
  const response = await fetch(item.blobUrl, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  const encrypted = await response.arrayBuffer();
  const keyBytes = base64ToArrayBuffer(item.encryption.keyBase64);
  const nonceBytes = base64ToArrayBuffer(item.encryption.nonceBase64);
  const key = await crypto.subtle.importKey(
    "raw",
    keyBytes,
    "AES-GCM",
    false,
    ["decrypt"],
  );
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: nonceBytes },
    key,
    encrypted,
  );
  return new Blob([plaintext], { type: item.contentType || "image/jpeg" });
}

function isMediaItem(value: unknown): value is MediaLibraryItem {
  const item = value as Partial<MediaLibraryItem>;
  return (
    typeof item?.id === "string" &&
    typeof item.source === "string" &&
    typeof item.localId === "string" &&
    typeof item.blobUrl === "string" &&
    typeof item.blossomOrigin === "string" &&
    typeof item.sha256 === "string" &&
    typeof item.encryptedSha256 === "string" &&
    typeof item.sizeBytes === "number" &&
    typeof item.contentType === "string" &&
    typeof item.encryption?.keyBase64 === "string" &&
    typeof item.encryption?.nonceBase64 === "string"
  );
}

function base64ToArrayBuffer(value: string): ArrayBuffer {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
