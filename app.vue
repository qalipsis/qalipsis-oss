<template>
  <a-config-provider
    :theme="{
      token: {
        colorPrimary: '#41c9ca',
      },
    }"
  >
    <a-layout>
      <a-layout-content v-if="canViewPage">
        <a-layout>
          <Sidebar />
          <a-layout-content style="max-height: 100vh; overflow-y: auto;">
            <NuxtPage />
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
  // Initializes the profile info
  const profile = await fetchProfile();

  // Updates the profile store info
  userStore.$patch({
    user: profile.user
  })

  _showPage();
})


const _showPage = async () => {
  // Updates the user permissions
  const permissions = await fetchPermissions();
  userStore.$patch({
    permissions: permissions
  });

  // Displays the page
  canViewPage.value = true;
}

</script>

<style scoped lang="scss">
:deep(.ant-layout) {
  background: white;
}
</style>