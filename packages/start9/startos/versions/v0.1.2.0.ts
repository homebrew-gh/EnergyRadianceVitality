import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:34',
  releaseNotes: {
    en_US:
      'Updated wellness info, new stadards settings',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
