import { sdk } from './sdk'

/**
 * ERV works with any user-provided Nostr relay URL. Local relay packages are
 * optional convenience integrations that main.ts probes when present.
 */
export const setDependencies = sdk.setupDependencies(async () => ({}))
