// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  devtools: { enabled: true },
  ssr: false,
  spaLoadingTemplate: false,

  app: {
    head: {
      title: 'QALIPSIS',
      htmlAttrs: {
        lang: 'en'
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
      ]
    }
  },

  modules: [
    "@vueuse/nuxt",
    "@pinia/nuxt",
    'nuxt-headlessui'
  ],

  runtimeConfig: {
    // Public keys that are exposed to the client
    public: {
      apiBaseUrl: process.env.API_BASE_URL || 'localhost:3000',
    }
  },

  css: [
    '~/assets/scss/main.scss'
  ],

  postcss: {
    plugins: {
      tailwindcss: {},
      autoprefixer: {},
    },
  },

  compatibilityDate: '2025-02-25',
})