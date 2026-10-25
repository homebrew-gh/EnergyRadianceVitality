import { sdk } from '../sdk'
import { resetCompanionKey } from './resetCompanionKey'

export const actions = sdk.Actions.of().addAction(resetCompanionKey)
