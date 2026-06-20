import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_1_0 = VersionInfo.of({
  version: '0.1.1:0',
  releaseNotes: {
    en_US:
      'Workout composer on web (circuits with full rest timers and prescriptions), ' +
      'erv/workouts/library sync, and Android workout library + live run improvements.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
