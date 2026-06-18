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

async function detectInstalledRelay(effects: any): Promise<DetectedRelay | null> {
  for (const candidate of relayCandidates) {
    const detected = await probeRelayCandidate(effects, candidate)
    if (detected) return detected
  }
  return null
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

  const detected = await detectInstalledRelay(effects)

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
  if (detected) {
    relayEnv.ERV_INTERNAL_RELAY_URL = detected.internal
    relayEnv.ERV_DETECTED_RELAY_LABEL = detected.label
    if (detected.suggested) {
      relayEnv.ERV_SUGGESTED_RELAY_URL = detected.suggested
    }
    console.info(
      `Linked relay detected: ${detected.label} at ${detected.internal}`,
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
