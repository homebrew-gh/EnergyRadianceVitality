import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:18',
  releaseNotes: {
    en_US:
      'Add relay editing and Haven migration diagnostics.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
