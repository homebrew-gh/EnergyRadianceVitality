import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_0_0 = VersionInfo.of({
  version: '0.1.0:0',
  releaseNotes: {
    en_US:
      'Initial ERV StartOS package: catalog editor plus weight, stretch, and cardio routine authoring. Syncs to Android via your Nostr relay (kind 30078, erv/* d-tags).',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
