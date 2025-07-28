<template>
  <div :class="{ dark: darkThemeEnabled }">
    <NuxtLayout v-if="isAppReady">
      <NuxtPage />
    </NuxtLayout>
    <PageLoader v-else />
    <BaseToaster :darkThemeEnabled="darkThemeEnabled"></BaseToaster>
  </div>
</template>

<script setup lang="ts">
const { fetchProfile, fetchPermissions } = useUserApi()

const userStore = useUserStore()
const toastStore = useToastStore()
const themeStore = useThemeStore()

const darkThemeEnabled = computed(() => themeStore.theme === 'dark')

/**
 * A flag to indicate if the page can be rendered.
 */
const isAppReady = ref(false)

onMounted(async () => {
  try {
    // Initializes the profile info
    const profile = await fetchProfile()
    const permissions = await fetchPermissions()

    // Updates the profile store info
    userStore.$patch({
      user: profile.user,
      permissions: permissions,
    })

    // Enabled the page to be initialized.
    isAppReady.value = true
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
})
</script>
