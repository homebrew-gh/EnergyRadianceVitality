import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:27',
  releaseNotes: {
    en_US:
      'Sideload of optional companion passphrase: skip at setup, add/change/remove in Settings, recover with nsec, DELETE wipe, and Reset companion key when stopped.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
