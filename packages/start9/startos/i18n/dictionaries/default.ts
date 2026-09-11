export const DEFAULT_LANG = 'en_US'

const dict = {
  'Starting ERV': 0,
  'Web UI': 1,
  'The ERV web UI is ready': 2,
  'The ERV web UI is not ready': 3,
  'ERV web interface': 4,
  'Reset companion key': 5,
  'Deletes the companion nsec from this service so Setup runs again. Relay events and the Android app are unchanged. Stop the service first.': 6,
  'You will need to paste the same nsec from Android. This does not use your passphrase.': 7,
  'Companion key reset': 8,
  'Start the service and open the Web UI to run Setup again.': 9,
  'Next step': 10,
} as const

export type I18nKey = keyof typeof dict
export type LangDict = Record<(typeof dict)[I18nKey], string>
export default dict
