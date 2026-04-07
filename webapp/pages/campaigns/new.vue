<template>
  <div v-if="campaignConfiguration">
    <BaseHeader>
      <div class="flex justify-between items-center w-full">
        <div class="flex items-center">
          <BaseIcon
            icon="qls-icon-arrow-back"
            class="cursor-pointer pl-1 pr-5 hover:text-primary-500 text-2xl"
            @click="handleBackBtnClick"
          />
          <BaseTitle
            v-model:content="campaignName"
            :editable="true"
          />
        </div>
        <div class="flex items-center">
          <BaseShowSelectedToggler
            @checkedChange="handleCheckedChange"
            :numberOfSelectedItems="selectedRowKeys.length"
          />
          <BaseTooltip text="Advanced configuration">
            <div
              class="cursor-pointer pl-2"
              @click="handleSettingBtnClick"
            >
              <BaseIcon
                icon="qls-icon-setting"
                class="text-2xl text-gray-600 dark:text-gray-100 hover:text-primary-500"
              />
            </div>
          </BaseTooltip>
          <BaseSearch
            class="ml-2"
            placeholder="Search scenario..."
            size="large"
            @search="handleSearch"
          />
          <BaseButton
            class="ml-2"
            :text="executionText"
            @click="handleRunBtnClick"
            :disabled="executionButtonDisabled"
          />
        </div>
      </div>
    </BaseHeader>
    <CampaignConfigDrawer
      v-if="open"
      :campaignConfigurationForm="campaignConfigForm"
      :disabled="false"
      v-model:open="open"
      @submit="handleCampaignConfigFormSubmit($event)"
    />
    <BaseModal
      v-if="defaultCampaignNameModalOpen"
      v-model:open="defaultCampaignNameModalOpen"
      title="Run immediately"
      confirmBtnText="Execute now"
      :closable="true"
      @confirmBtnClick="handleDefaultCampaignNameModalConfirmBtnClick"
    >
      <section>
        <span>You haven't set a custom name for the new campaign. Do you want to run it anyway?</span>
      </section>
    </BaseModal>
    <BaseContentWrapper>
      <ScenarioTable :campaignConfiguration="campaignConfiguration" />
    </BaseContentWrapper>
  </div>
</template>

<script setup lang="ts">
const { fetchDefaultCampaignConfiguration } = useConfigurationApi()

const scenarioTableStore = useScenarioTableStore()

const { selectedRowKeys, scenarioConfig } = storeToRefs(scenarioTableStore)

const campaignConfiguration = ref<DefaultCampaignConfiguration>()

const { executeCampaign } = useCampaignExecution(campaignConfiguration)
const campaignName = ref('New Campaign')
const hasCustomName = ref(false)
const open = ref(false)
const defaultCampaignNameModalOpen = ref(false)
const campaignConfigForm = ref<CampaignConfigurationForm>({
  timeoutType: TimeoutTypeConstant.NONE,
  durationValue: '',
  durationUnit: TimeframeUnitConstant.MS,
  scheduled: false,
  repeatEnabled: false,
  repeatTimeRange: 'DAILY',
  repeatValues: [],
  relativeRepeatValues: [],
  timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  scheduledTime: null,
})

const executionText = computed(() => {
  return campaignConfigForm.value?.scheduled ? 'Schedule' : 'Run immediately'
})

const executionButtonDisabled = computed(() => {
  return (
    selectedRowKeys.value.length === 0 ||
    selectedRowKeys.value.some((selectedRowKey) => !scenarioConfig.value[selectedRowKey])
  )
})

onMounted(async () => {
  campaignConfiguration.value = await fetchDefaultCampaignConfiguration()
})

onBeforeUnmount(() => scenarioTableStore.$reset())

const stopNameWatcher = watch(campaignName, () => {
  hasCustomName.value = true
  stopNameWatcher()
})

const handleBackBtnClick = () => {
  navigateTo('/campaigns')
}

const handleSearch = (searchTerm: string) => {
  scenarioTableStore.$patch({
    query: searchTerm,
    currentPageIndex: 0,
  })
  scenarioTableStore.refreshScenarios()
}

const handleCheckedChange = (checked: boolean) => {
  scenarioTableStore.$patch({
    currentPageIndex: 0,
    dataSource: checked ? scenarioTableStore.selectedRows : scenarioTableStore.allScenarios,
    totalElements: checked ? scenarioTableStore.selectedRows.length : scenarioTableStore.allScenarios.length,
  })
}

const handleSettingBtnClick = () => {
  open.value = true
}

const handleCampaignConfigFormSubmit = (form: CampaignConfigurationForm) => {
  campaignConfigForm.value = form
}

const handleRunBtnClick = () => {
  if (!hasCustomName.value && !campaignConfigForm.value?.scheduled) {
    defaultCampaignNameModalOpen.value = true
  } else {
    _executeCampaign()
  }
}

const handleDefaultCampaignNameModalConfirmBtnClick = () => {
  _executeCampaign()
}

const _executeCampaign = () => executeCampaign(campaignName.value, campaignConfigForm.value)
</script>
