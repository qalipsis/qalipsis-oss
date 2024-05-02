<template>
  <div>
    <section
      v-if="!canViewPage"
      class="flex items-center justify-center w-screen h-screen"
    >
      <div class="flex items-center">
        <BaseIcon icon="/icons/icon-logo.svg" width="80" />
        <h1 class="text-primary-500 mr-2 text-2xl font-semibold">QALIPSIS</h1>
        <BaseSpinner size="md" />
      </div>
    </section>
    <section v-if="canViewPage" class="flex w-screen h-screen">
      <Sidebar />
      <div class="max-h-screen overflow-y-auto bg-white text-primary-950 flex-grow" >
        <NuxtLayout>
          <NuxtPage />
        </NuxtLayout>
      </div>
    </section>
  </div>
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
