/** Optional StartOS AI packages probed at startup. */
export type StartOsAiCandidate = {
  packageId: string
  interfaceId: string
  internalPort: number
  label: string
  provider: 'MAPLE'
}

export const aiEndpointCandidates: StartOsAiCandidate[] = [
  {
    packageId: 'maple-proxy',
    interfaceId: 'api',
    internalPort: 8080,
    label: 'Maple Proxy',
    provider: 'MAPLE',
  },
]

export function canonicalInternalAiUrl(candidate: StartOsAiCandidate): string {
  return `http://${candidate.packageId}.startos:${candidate.internalPort}`
}

/**
 * Pick the container-to-container base URL for an OpenAI-compatible API.
 * Returns null when the interface is not present (package not installed).
 */
export function aiBaseUrlFromInterface(
  urls: string[] | null | undefined,
  fallbackInternal: string,
): string | null {
  if (urls == null) return null

  const fromInterface = urls.find((url) => {
    try {
      const parsed = new URL(url)
      return (
        (parsed.protocol === 'http:' || parsed.protocol === 'https:') &&
        parsed.hostname.endsWith('.startos')
      )
    } catch {
      return false
    }
  })

  if (!fromInterface) return fallbackInternal.replace(/\/$/, '')

  const internal = fromInterface.startsWith('https://')
    ? fromInterface.replace(/^https:\/\//, 'http://')
    : fromInterface
  return internal.replace(/\/$/, '')
}
