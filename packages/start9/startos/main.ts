import { i18n } from './i18n'
import { sdk } from './sdk'
import {
  aiBaseUrlFromInterface,
  aiEndpointCandidates,
  canonicalInternalAiUrl,
  type StartOsAiCandidate,
} from './ai'
import {
  canonicalInternalRelayUrl,
  relayCandidates,
  relayUrlsFromInterface,
  type StartOsRelayCandidate,
} from './relay'
import { uiPort } from './utils'

type DetectedRelay = {
  internal: string
  suggested: string | null
  label: string
}

function dedupeDetectedRelays(relays: DetectedRelay[]): DetectedRelay[] {
  const seen = new Set<string>()
  return relays.filter((relay) => {
    if (seen.has(relay.internal)) return false
    seen.add(relay.internal)
    return true
  })
}

async function detectInstalledRelays(effects: any): Promise<DetectedRelay[]> {
  const detected: DetectedRelay[] = []
  for (const candidate of relayCandidates) {
    const probed = await probeRelayCandidate(effects, candidate)
    if (probed) detected.push(probed)
  }
  return dedupeDetectedRelays(detected)
}

async function probeRelayCandidate(
  effects: any,
  candidate: StartOsRelayCandidate,
): Promise<DetectedRelay | null> {
  const formattedUrls = await sdk.serviceInterface
    .get(
      effects,
      { id: candidate.interfaceId, packageId: candidate.packageId },
      (i) => i?.addressInfo?.format() ?? null,
    )
    .const()

  const parsed = relayUrlsFromInterface(
    formattedUrls,
    canonicalInternalRelayUrl(candidate),
  )
  if (!parsed) return null

  return { ...parsed, label: candidate.label }
}

type DetectedAiEndpoint = {
  label: string
  provider: 'MAPLE'
  base_url: string
}

async function detectInstalledAiEndpoints(
  effects: any,
): Promise<DetectedAiEndpoint[]> {
  const detected: DetectedAiEndpoint[] = []
  for (const candidate of aiEndpointCandidates) {
    const probed = await probeAiCandidate(effects, candidate)
    if (probed) detected.push(probed)
  }
  return detected
}

async function probeAiCandidate(
  effects: any,
  candidate: StartOsAiCandidate,
): Promise<DetectedAiEndpoint | null> {
  const formattedUrls = await sdk.serviceInterface
    .get(
      effects,
      { id: candidate.interfaceId, packageId: candidate.packageId },
      (i) => i?.addressInfo?.format() ?? null,
    )
    .const()

  const baseUrl = aiBaseUrlFromInterface(
    formattedUrls,
    canonicalInternalAiUrl(candidate),
  )
  if (!baseUrl) return null
  return {
    label: candidate.label,
    provider: candidate.provider,
    base_url: baseUrl,
  }
}

export const main = sdk.setupMain(async ({ effects }) => {
  console.info(i18n('Starting ERV'))

  const detectedRelays = await detectInstalledRelays(effects)
  const detected = detectedRelays[0] ?? null
  const detectedAi = await detectInstalledAiEndpoints(effects)

  const subcontainer = await sdk.SubContainer.of(
    effects,
    { imageId: 'main' },
    sdk.Mounts.of().mountVolume({
      volumeId: 'main',
      subpath: null,
      mountpoint: '/data',
      readonly: false,
    }),
    'erv-web-sub',
  )

  const daemonEnv: Record<string, string> = {}
  if (detectedRelays.length > 0) {
    daemonEnv.ERV_DETECTED_RELAYS_JSON = JSON.stringify(
      detectedRelays.map(({ label, internal, suggested }) => ({
        label,
        internal,
        suggested,
      })),
    )
    daemonEnv.ERV_INTERNAL_RELAY_URL = detected!.internal
    daemonEnv.ERV_DETECTED_RELAY_LABEL = detected!.label
    if (detected!.suggested) {
      daemonEnv.ERV_SUGGESTED_RELAY_URL = detected!.suggested
    }
    console.info(
      `Linked relays detected: ${detectedRelays.map((r) => `${r.label}@${r.internal}`).join(', ')}`,
    )
  } else {
    console.info(
      'No local Nostr relay detected — configure an external wss:// relay during setup',
    )
  }

  if (detectedAi.length > 0) {
    daemonEnv.ERV_DETECTED_AI_ENDPOINTS_JSON = JSON.stringify(detectedAi)
    console.info(
      `AI endpoints detected: ${detectedAi.map((endpoint) => `${endpoint.label}@${endpoint.base_url}`).join(', ')}`,
    )
  } else {
    console.info('No Maple Proxy detected — AI coach stays off until an endpoint is configured')
  }

  return sdk.Daemons.of(effects).addDaemon('primary', {
    subcontainer,
    exec: {
      command: ['/usr/local/bin/erv-web'],
      env: daemonEnv,
    },
    ready: {
      display: i18n('Web UI'),
      fn: () =>
        sdk.healthCheck.checkPortListening(effects, uiPort, {
          successMessage: i18n('The ERV web UI is ready'),
          errorMessage: i18n('The ERV web UI is not ready'),
        }),
    },
    requires: [],
  })
})
