import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:30',
  releaseNotes: {
    en_US:
      'Workout Builder recovers workouts a stale relay index missed, and saves now publish only changed workouts plus the index (2 events instead of the whole library) to stay under Haven outbox rate limits.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
