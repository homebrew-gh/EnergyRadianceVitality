import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:26',
  releaseNotes: {
    en_US:
      'Companion passphrase is optional. Skip at setup or add, change, or remove it later in Settings. Unlock can recover with the same nsec; wipe needs DELETE only. StartOS has a Reset companion key action when the service is stopped.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
