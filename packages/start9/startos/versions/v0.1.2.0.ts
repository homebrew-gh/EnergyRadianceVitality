import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:19',
  releaseNotes: {
    en_US:
      'Add web weekly planner publishing, profile preset updates, and AI progression guardrail context.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
