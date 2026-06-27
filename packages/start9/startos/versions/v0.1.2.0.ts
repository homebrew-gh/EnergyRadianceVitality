import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:17',
  releaseNotes: {
    en_US:
      'Prefer Haven for local relay setup and add the Blossom media backup foundation.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
