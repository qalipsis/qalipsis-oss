<template>
  <div v-if="campaignConfigForm && campaignConfiguration">
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
            v-if="!campaignKey"
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
            v-if="!campaignKey"
            class="ml-2"
            placeholder="Search scenario..."
            size="large"
            @search="handleSearch"
          />
          <BaseButton
            class="ml-2"
            v-if="campaignKey"
            theme="error"
            text="Unschedule"
            @click="handleUnscheduleBtnClick"
            :disabled="executionButtonDisabled"
          />
          <BaseButton
            class="ml-2"
            v-if="!campaignKey"
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
      :disabled="campaignKey !== undefined"
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
    <CampaignsAbortModal
      v-if="campaignKey && campaignName"
      v-model:open="campaignAbortModalOpen"
      :campaign-key="campaignKey"
      :campaign-name="campaignName"
      @aborted="handleCampaignAborted()"
    />
    <BaseContentWrapper>
      <ScenarioTable :campaignConfiguration="campaignConfiguration" />
    </BaseContentWrapper>
  </div>
</template>

<script setup lang="ts">
const { fetchCampaignConfiguration } = useCampaignApi()
const { fetchDefaultCampaignConfiguration } = useConfigurationApi()

const scenarioTableStore = useScenarioTableStore()
const toastStore = useToastStore()
const route = useRoute()

const { selectedRowKeys, scenarioConfig } = storeToRefs(scenarioTableStore)

const campaignConfiguration = ref<DefaultCampaignConfiguration>()
const campaignConfigForm = ref<CampaignConfigurationForm>()
const campaignName = ref<string>()
const campaignKey = ref<string>()

const { executeCampaign } = useCampaignExecution(campaignConfiguration, campaignKey)
const open = ref(false)
const defaultCampaignNameModalOpen = ref(false)
const campaignAbortModalOpen = ref(false)

const DEFAULT_CAMPAIGN_NAME = 'New Campaign'

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
  try {
    campaignKey.value = route.params.id as string
    campaignConfiguration.value = await fetchDefaultCampaignConfiguration()
    const campaignConfig = await fetchCampaignConfiguration(campaignKey.value)
    campaignName.value = campaignConfig.name
    const fetchedForm = CampaignHelper.toCampaignConfigForm(campaignConfig)
    campaignConfigForm.value = {
      timeoutType: fetchedForm?.timeoutType ?? TimeoutTypeConstant.NONE,
      durationValue: fetchedForm?.durationValue ?? '',
      durationUnit: fetchedForm?.durationUnit ?? TimeframeUnitConstant.MS,
      scheduled: fetchedForm?.scheduled ?? false,
      repeatEnabled: fetchedForm?.repeatEnabled ?? false,
      repeatTimeRange: fetchedForm?.repeatTimeRange ?? 'DAILY',
      repeatValues: fetchedForm?.repeatValues ?? [],
      relativeRepeatValues: fetchedForm?.relativeRepeatValues ?? [],
      timezone: fetchedForm?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
      scheduledTime: fetchedForm?.scheduledTime ?? null,
    }
    const selectedScenarios: Scenario[] = Object.entries(campaignConfig.scenarios).map(([key, value]) => ({
      name: key,
      minionsCount: value.minionsCount,
      version: '',
    }))
    const selectedScenarioKeys = Object.keys(campaignConfig.scenarios)
    const scenarioKeyToScenarioForm = ScenarioHelper.toScenarioConfigForm(campaignConfig)
    scenarioTableStore.$patch({
      selectedRowKeys: selectedScenarioKeys,
      selectedRows: selectedScenarios,
      scenarioConfig: scenarioKeyToScenarioForm,
      rowSelectionEnabled: false,
    })
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
})

onBeforeUnmount(() => scenarioTableStore.$reset())

const handleBackBtnClick = () => {
  navigateTo('/campaigns')
}

const handleUnscheduleBtnClick = () => {
  campaignAbortModalOpen.value = true
}

const handleCampaignAborted = () => {
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
  if (campaignName.value === DEFAULT_CAMPAIGN_NAME && !campaignConfigForm.value?.scheduled) {
    defaultCampaignNameModalOpen.value = true
  } else {
    _executeCampaign()
  }
}

const handleDefaultCampaignNameModalConfirmBtnClick = () => {
  _executeCampaign()
}

const _executeCampaign = () => {
  if (!campaignName.value || !campaignConfigForm.value) return
  executeCampaign(campaignName.value, campaignConfigForm.value)
}
</script>
