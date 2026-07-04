import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:25',
  releaseNotes: {
    en_US:
      'Workout Builder uses a 3-column layout so the compose editor aligns with saved workouts and the publish panel sits in its own column.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
