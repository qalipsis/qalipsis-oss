<template>
  <a-layout>
    <a-layout-content v-if="canViewPage">
      <a-layout>
        <Sidebar />
        <a-layout-content>
          <NuxtPage />
        </a-layout-content>
      </a-layout>
    </a-layout-content>
    <BaseModal 
      title="Select tenant"
      v-model:open="tenantModalOpen"
      @confirm-btn-click="handleSelectTenantConfirmButtonClick()">
      <FormSelect 
        v-model="currentTenantReference"
        :options="tenantOptions">
      </FormSelect>
    </BaseModal>
  </a-layout>
</template>

<script setup lang="ts">

const { fetchProfile } = useUserApi();
const userStore = useUserStore();

const currentTenantReference = ref(userStore.currentTenantReference);

/**
 * A flag to indicate if the tenant modal should open
 */
const tenantModalOpen = ref(false);

/**
 * The select options for the tenant modal
 */
const tenantOptions = ref<FormDropdownOption[]>([]);

/**
 * A flag to indicate the if the page can be displayed.
 */
const canViewPage = ref(false);

onMounted(async () => {
  // Initializes the profile info
  const profile = await fetchProfile();

  // Updates the profile store info
  userStore.$patch({
    user: profile.user,
    tenants: profile.tenants
  })

  // Displays the page when the current tenant reference is not empty,
  // or there is only one tenant option
  if (userStore.currentTenantReference || profile.tenants.length === 1) {
    const tenantToStore = userStore.currentTenantReference ?? profile.tenants[0].reference;
    userStore.storeTenant(tenantToStore);
    _showPage();

    return;
  }

  _showTenantModal();

})

const handleSelectTenantConfirmButtonClick = () => {
  userStore.storeTenant(currentTenantReference.value!);
  _showPage();
}

const _showTenantModal = () => {
  // Sets the tenant options
  tenantOptions.value = userStore.tenants.map(tenant => ({
    label: tenant.displayName,
    value: tenant.reference
  }));

  // Sets the first tenant option as the default selected value for the dropdown
  currentTenantReference.value = tenantOptions.value[0].value;

  // Hides the page 
  canViewPage.value = false;

  // Displays the modal
  tenantModalOpen.value = true;
}

const _showPage = async () => {
  // Closes the tenant modal
  tenantModalOpen.value = false;
  // Displays the page
  canViewPage.value = true;
}

</script>

<style scoped lang="scss">
.ant-layout:deep {
  background: white;
}
</style>