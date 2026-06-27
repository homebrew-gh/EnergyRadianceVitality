import { setupManifest } from '@start9labs/start-sdk'
import { installAlert, long, short, updateAlert } from './i18n'

export const manifest = setupManifest({
  id: 'erv-web',
  title: 'ERV Web Companion',
  license: 'MIT',
  packageRepo: 'https://github.com/samcornwell/EnergyRadianceVitality',
  upstreamRepo: 'https://github.com/samcornwell/EnergyRadianceVitality',
  marketingUrl: 'https://github.com/samcornwell/EnergyRadianceVitality',
  donationUrl: null,
  description: { short, long },
  volumes: ['main'],
  images: {
    main: {
      source: {
        dockerBuild: {
          workdir: '../../apps/web',
        },
      },
      arch: ['x86_64', 'aarch64'],
    },
  },
  alerts: {
    install: installAlert,
    update: updateAlert,
    uninstall: null,
    restore: null,
    start: null,
    stop: null,
  },
  dependencies: {
    haven: {
      description:
        'Recommended personal Nostr relay for ERV sync. Haven also provides Blossom media storage for future image backup features.',
      optional: true,
      s9pk: null,
      metadata: {
        title: 'Haven',
        icon: '../assets/haven.svg',
      },
    },
  },
})
