import { i18n } from '../i18n'
import { companionStateJson } from '../fileModels/state.json'
import { sdk } from '../sdk'

export const resetCompanionKey = sdk.Action.withoutInput(
  'reset-companion-key',
  async () => ({
    name: i18n('Reset companion key'),
    description: i18n(
      'Deletes the companion nsec from this service so Setup runs again. Relay events and the Android app are unchanged. Stop the service first.',
    ),
    warning: i18n(
      'You will need to paste the same nsec from Android. This does not use your passphrase.',
    ),
    allowedStatuses: 'only-stopped',
    group: null,
    visibility: 'enabled',
  }),
  async ({ effects }) => {
    await companionStateJson.write(effects, {
      v: 1,
      sealed: null,
      nsec: undefined,
      relay_url: null,
      relay_urls: [],
      npub: null,
    })

    return {
      version: '1',
      title: i18n('Companion key reset'),
      message: i18n(
        'Start the service and open the Web UI to run Setup again.',
      ),
      result: {
        type: 'single',
        name: i18n('Next step'),
        description: null,
        value: 'Setup',
        masked: false,
        copyable: false,
        qr: false,
      },
    }
  },
)
