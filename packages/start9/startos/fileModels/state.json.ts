import { FileHelper, z } from '@start9labs/start-sdk'
import { sdk } from '../sdk'

const shape = z.object({
  v: z.number().catch(1),
  sealed: z.unknown().nullable().catch(null),
  nsec: z.string().optional(),
  relay_url: z.string().nullable().catch(null),
  relay_urls: z.array(z.string()).catch([]),
  npub: z.string().nullable().catch(null),
})

export const companionStateJson = FileHelper.json(
  { base: sdk.volumes.main, subpath: 'state.json' },
  shape,
)
