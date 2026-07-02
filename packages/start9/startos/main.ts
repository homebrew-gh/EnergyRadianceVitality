import { i18n } from './i18n'
import { sdk } from './sdk'
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

export const main = sdk.setupMain(async ({ effects }) => {
  console.info(i18n('Starting ERV'))

  const detectedRelays = await detectInstalledRelays(effects)
  const detected = detectedRelays[0] ?? null

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

  const relayEnv: Record<string, string> = {}
  if (detectedRelays.length > 0) {
    relayEnv.ERV_DETECTED_RELAYS_JSON = JSON.stringify(
      detectedRelays.map(({ label, internal, suggested }) => ({
        label,
        internal,
        suggested,
      })),
    )
    relayEnv.ERV_INTERNAL_RELAY_URL = detected!.internal
    relayEnv.ERV_DETECTED_RELAY_LABEL = detected!.label
    if (detected!.suggested) {
      relayEnv.ERV_SUGGESTED_RELAY_URL = detected!.suggested
    }
    console.info(
      `Linked relays detected: ${detectedRelays.map((r) => `${r.label}@${r.internal}`).join(', ')}`,
    )
  } else {
    console.info(
      'No local Nostr relay detected — configure an external wss:// relay during setup',
    )
  }

  return sdk.Daemons.of(effects).addDaemon('primary', {
    subcontainer,
    exec: {
      command: ['/usr/local/bin/erv-web'],
      env: relayEnv,
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
