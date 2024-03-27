<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#41c9ca',
      },
    }"
  >
    <section
      v-if="!canViewPage"
      class="loading-section flex items-center content-center"
    >
      <div class="flex items-center">
        <BaseIcon icon="/icons/icon-logo.svg" width="80" />
        <h1 class="text-primary-color mr-4">QALIPSIS</h1>
        <div class="mt-2">
          <a-spin size="large" />
        </div>
      </div>
    </section>
    <a-layout>
      <a-layout-content v-if="canViewPage">
        <a-layout>
          <Sidebar />
          <a-layout-content style="max-height: 100vh; overflow-y: auto">
            <NuxtLayout>
              <NuxtPage />
            </NuxtLayout>
          </a-layout-content>
        </a-layout>
      </a-layout-content>
    </a-layout>
  </a-config-provider>
</template>

<script setup lang="ts">
const { fetchProfile, fetchPermissions } = useUserApi();
const userStore = useUserStore();

/**
 * A flag to indicate the if the page can be displayed.
 */
const canViewPage = ref(false);

onMounted(async () => {
  try {
    // Initializes the profile info
    const profile = await fetchProfile();

    // Updates the profile store info
    userStore.$patch({
      user: profile.user,
    });

    _showPage();
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error)
  }
});

const _showPage = async () => {
  // Updates the user permissions
  const permissions = await fetchPermissions();
  userStore.$patch({
    permissions: permissions,
  });

  // Displays the page
  canViewPage.value = true;
};
</script>

<style scoped lang="scss">
:deep(.ant-layout) {
  background: white;
}

.loading-section {
  width: 100vw;
  height: 100vh;

  .spinner-wrapper {
    width: 4rem;
    height: 4rem;
  }
}
</style>
