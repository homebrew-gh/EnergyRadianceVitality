import { VersionGraph } from '@start9labs/start-sdk'
import { v_0_1_0_0 } from './v0.1.0.0'
import { v_0_1_1_0 } from './v0.1.1.0'
import { v_0_1_2_0 } from './v0.1.2.0'

export const versionGraph = VersionGraph.of({
  current: v_0_1_2_0,
  other: [v_0_1_0_0, v_0_1_1_0],
})
