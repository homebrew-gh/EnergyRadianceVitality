export const DEFAULT_LANG = 'en_US'

const dict = {
  'Starting ERV': 0,
  'Web UI': 1,
  'The ERV web UI is ready': 2,
  'The ERV web UI is not ready': 3,
  'ERV web interface': 4,
} as const

export type I18nKey = keyof typeof dict
export type LangDict = Record<(typeof dict)[I18nKey], string>
export default dict
