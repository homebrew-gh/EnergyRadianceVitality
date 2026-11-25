import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:33',
  releaseNotes: {
    en_US:
      'Updated AI coach. New JSON pasring and updated workout data.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
