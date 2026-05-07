/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import { fileURLToPath } from 'node:url'
import { resolve, dirname } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  devtools: { enabled: true },
  ssr: false,
  spaLoadingTemplate: false,

  app: {
    head: {
      title: 'QALIPSIS',
      htmlAttrs: {
        lang: 'en',
      },
      meta: [
        {
          name: 'description',
          content: 'Load and performance test tool',
        },
        {
          name: 'format-detection',
          content: 'telephone=no',
        },
      ],
    },
  },

  modules: ['@vueuse/nuxt', '@pinia/nuxt', 'nuxt-headlessui'],

  imports: {
    dirs: ['types', 'types/api', 'utils/helpers', 'utils/configs', 'composables/apis', 'stores'],
  },

  runtimeConfig: {
    // Public keys that are exposed to the client
    public: {
      projectName: 'webapp-oss',
      apiBaseUrl: process.env.API_BASE_URL || 'localhost:3000',
    },
  },

  css: [resolve(__dirname, 'app/assets/scss/main.scss')],

  postcss: {
    plugins: {
      tailwindcss: {},
      autoprefixer: {},
    },
  },

  vite: {
    optimizeDeps: {
      include: [
        'apexcharts',
        'vue3-apexcharts',
        '@vuepic/vue-datepicker',
        '@ckpack/vue-color',
        '@headlessui/vue',
        'fuse.js',
        'luxon',
        'date-fns',
        'tinycolor2',
        'tippy.js',
        '@vue/devtools-core',
        '@vue/devtools-kit',
        'vee-validate',
        '@vee-validate/zod',
        'zod',
      ],
    },
  },

  typescript: {
    tsConfig: {
      exclude: ['../.output', '../.gradle', '../build', '../src'],
    },
  },

  compatibilityDate: '2025-04-21',
})
