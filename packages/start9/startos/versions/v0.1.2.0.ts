import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:23',
  releaseNotes: {
    en_US:
      'Blossom media proxy and authenticated probe; Progress session images; Android auto-backups for GPS routes and HR graphs; relay sync and workout library improvements.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
