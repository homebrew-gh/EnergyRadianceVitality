import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:27',
  releaseNotes: {
    en_US:
      'Optional AI coach: detect Maple Proxy, test the connection, and stream a sectioned training review on Progress. Workout import preview compares an envelope with a saved workout before publish.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
