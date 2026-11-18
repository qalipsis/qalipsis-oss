<!--
  - QALIPSIS
  - Copyright (C) 2025 AERIS IT Solutions GmbH
  -
  - This program is dual-licensed: you may use, modify and distribute it under the terms of either
  -
  -   1. the GNU Affero General Public License as published by the Free Software Foundation,
  -      either version 3 of the License, or (at your option) any later version; or
  -   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
  -
  - Under the GNU Affero General Public License, this program is distributed in the hope that it
  - will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  - For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
  -
  -->

<template>
  <div :class="{ dark: darkThemeEnabled }">
    <NuxtLayout v-if="isAppReady">
      <NuxtPage/>
    </NuxtLayout>
    <PageLoader v-else/>
    <BaseToaster :darkThemeEnabled="darkThemeEnabled"></BaseToaster>
  </div>
</template>

<script setup lang="ts">
const {fetchProfile, fetchPermissions} = useUserApi()

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
    toastStore.error({text: ErrorHelper.getErrorMessage(error)})
  }
})
</script>
