import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:2',
  releaseNotes: {
    en_US:
      'Title case field labels and section headers on web and Android; StartOS builds auto-bump version for in-place sideload updates.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
