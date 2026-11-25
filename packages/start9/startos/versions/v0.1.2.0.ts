import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:32',
  releaseNotes: {
    en_US:
      'Workout builder updates and new AI coach settings.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
