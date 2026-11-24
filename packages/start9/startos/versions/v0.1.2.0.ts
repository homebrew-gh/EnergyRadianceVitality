import { IMPOSSIBLE, VersionInfo } from '@start9labs/start-sdk'

export const v_0_1_2_0 = VersionInfo.of({
  version: '0.1.2:31',
  releaseNotes: {
    en_US:
      'Workout builder: weights in pounds everywhere, a plate calculator, separate Save Draft and Publish (unpublished drafts are clearly marked), a phone compatibility check before publishing, safer editing and saving, interval legs set in exact seconds, and shared example workouts. Workout deletes now sync across devices. The sign-in passphrase is optional, with nsec recovery.',
  },
  migrations: {
    up: async () => {},
    down: IMPOSSIBLE,
  },
})
