/** Optional StartOS Nostr relay packages probed at startup (first match wins). */
export type StartOsRelayCandidate = {
  packageId: string
  interfaceId: string
  internalPort: number
  label: string
}

export const relayCandidates: StartOsRelayCandidate[] = [
  {
    packageId: 'haven',
    interfaceId: 'relay',
    internalPort: 3355,
    label: 'Haven',
  },
  {
    packageId: 'nostr-rs-relay',
    interfaceId: 'relay',
    internalPort: 8080,
    label: 'Nostr RS Relay',
  },
]

export function canonicalInternalRelayUrl(
  candidate: StartOsRelayCandidate,
): string {
  return `ws://${candidate.packageId}.startos:${candidate.internalPort}`
}

export type ParsedRelayUrls = {
  internal: string
  suggested: string | null
}

/**
 * Pick container-to-container relay URLs from a StartOS service interface.
 * Returns null when the interface is not present (relay package not installed).
 */
export function relayUrlsFromInterface(
  urls: string[] | null | undefined,
  fallbackInternal: string,
): ParsedRelayUrls | null {
  if (urls == null) return null

  const fromInterface =
    urls.find((u) => u.startsWith('ws://') && u.includes('.startos')) ??
    urls.find((u) => {
      try {
        return new URL(u).hostname.endsWith('.startos')
      } catch {
        return false
      }
    })

  let internal: string
  if (fromInterface) {
    internal = fromInterface.startsWith('wss://')
      ? fromInterface.replace(/^wss:\/\//, 'ws://')
      : fromInterface
  } else {
    internal = fallbackInternal
  }

  const suggested = urls.find((u) => u.startsWith('wss://')) ?? null
  return { internal, suggested }
}
