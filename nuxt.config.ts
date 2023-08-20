// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  devtools: { enabled: true },
  ssr: false,
  app: {
    head: {
      title: 'Qalipsis',
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
    "@pinia/nuxt",
    "@ant-design-vue/nuxt"
  ],
  runtimeConfig: {
    // Public keys that are exposed to the client
    public: {
      apiBaseUrl: process.env.API_BASE_URL || 'localhost:3000',
    }
  },
  imports: {
    dirs: [
      "composables/**",
      "utils/**",
    ]
  },
  css: [
    '@/assets/scss/main.scss'
  ],
})
