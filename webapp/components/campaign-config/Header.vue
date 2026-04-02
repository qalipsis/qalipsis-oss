<template>
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
        <BaseSwitch
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
      <span>You haven’t set a custom name for the new campaign. Do you want to run it anyway?</span>
    </section>
  </BaseModal>
  <CampaignsAbortModal
    v-if="campaignKey && campaignName"
    v-model:open="campaignAbortModalOpen"
    :campaign-key="campaignKey"
    :campaign-name="campaignName"
    @aborted="handleCampaignAborted()"
  ></CampaignsAbortModal>
</template>

<script setup lang="ts">
const scenarioTaleStore = useScenarioTableStore()
const toastStore = useToastStore()

const { selectedRowKeys, scenarioConfig } = storeToRefs(scenarioTaleStore)

const props = defineProps<{
  campaignConfiguration: DefaultCampaignConfiguration
  campaignKey?: string
  campaignName?: string
  campaignConfigForm?: CampaignConfigurationForm
}>()

const { createCampaign, scheduleCampaign, updateCampaignConfig } = useCampaignApi()

const campaignAbortModalOpen = ref(false)
const campaignConfigForm = ref<CampaignConfigurationForm>({
  timeoutType: props.campaignConfigForm?.timeoutType ?? TimeoutTypeConstant.NONE,
  durationValue: props.campaignConfigForm?.durationValue ?? '',
  durationUnit: props.campaignConfigForm?.durationUnit ?? TimeframeUnitConstant.MS,
  scheduled: props.campaignConfigForm?.scheduled ?? false,
  repeatEnabled: props.campaignConfigForm?.repeatEnabled ?? false,
  repeatTimeRange: props.campaignConfigForm?.repeatTimeRange ?? 'DAILY',
  repeatValues: props.campaignConfigForm?.repeatValues ?? [],
  relativeRepeatValues: props.campaignConfigForm?.relativeRepeatValues ?? [],
  // Uses the default time zone from the user browser as the default value.
  timezone: props.campaignConfigForm?.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone,
  scheduledTime: props.campaignConfigForm?.scheduledTime ?? null,
})

const DEFAULT_CAMPAIGN_NAME = 'New Campaign'

const campaignName = ref(props.campaignName ?? DEFAULT_CAMPAIGN_NAME)
const open = ref(false)
const defaultCampaignNameModalOpen = ref(false)
const executionText = computed(() => {
  return campaignConfigForm.value?.scheduled ? 'Schedule' : 'Run immediately'
})

const executionButtonDisabled = computed(() => {
  return (
    selectedRowKeys.value.length === 0 ||
    selectedRowKeys.value.some((selectedRowKey) => !scenarioConfig.value[selectedRowKey])
  )
})

const handleUnscheduleBtnClick = () => {
  campaignAbortModalOpen.value = true
}

const handleCampaignAborted = () => {
  navigateTo('/campaigns')
}

const handleBackBtnClick = () => {
  navigateTo('/campaigns')
}

const handleSearch = (searchTerm: string) => {
  scenarioTaleStore.$patch({
    query: searchTerm,
  })

  // When search event is triggered, always resets the current page index to be 0.
  scenarioTaleStore.$patch({
    currentPageIndex: 0,
  })

  scenarioTaleStore.refreshScenarios()
}

const handleCheckedChange = (checked: boolean) => {
  // When show checked event is changed, always resets the current page index to be 0.
  scenarioTaleStore.$patch({
    currentPageIndex: 0,
    dataSource: checked ? scenarioTaleStore.selectedRows : scenarioTaleStore.allScenarios,
    totalElements: checked ? scenarioTaleStore.selectedRows.length : scenarioTaleStore.allScenarios.length,
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

const _executeCampaign = async () => {
  const selectedScenarioConfigMap: {
    [key: string]: ScenarioConfigurationForm
  } = {}
  const defaultStage = props.campaignConfiguration.validation.stage

  scenarioTaleStore.selectedRows.forEach((scenario) => {
    if (scenarioTaleStore.scenarioConfig[scenario.name]) {
      selectedScenarioConfigMap[scenario.name] = {
        ...scenarioTaleStore.scenarioConfig[scenario.name],
        executionProfileStages: scenarioTaleStore.scenarioConfig[scenario.name].executionProfileStages.map(
          (executionProfileStage) => ({
            ...executionProfileStage,
            // Sets the min resolution from the campaign configuration to each execution profile stages.
            resolution: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minResolution),
          })
        ),
      }
    } else {
      const defaultScenarioForm: ScenarioConfigurationForm = {
        executionProfileStages: [
          {
            minionsCount: defaultStage.minMinionsCount,
            duration: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minDuration),
            rampUpDuration: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minStartDuration),
            resolution: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minResolution),
          },
        ],
        zones: [],
      }
      selectedScenarioConfigMap[scenario.name] = defaultScenarioForm
    }
  })

  const request = CampaignHelper.toCampaignConfiguration(
    campaignName.value,
    campaignConfigForm.value!,
    selectedScenarioConfigMap
  )

  try {
    if (campaignConfigForm.value.scheduled) {
      if (props.campaignKey) {
        // Updates the existing scheduled campaign
        await updateCampaignConfig(props.campaignKey, request)
      } else {
        // Creates a new scheduled campaign
        await scheduleCampaign(request)
      }

      navigateTo('/campaigns')
    } else {
      // Creates a new campaign
      const newCampaign = await createCampaign(request)
      // navigate to the campaign details
      navigateTo(`/campaigns/${newCampaign.key}`)
    }
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
