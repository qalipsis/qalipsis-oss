<template>
  <template v-if="isReady">
    <BaseHeader>
      <div class="flex justify-between items-center w-full">
        <div class="flex items-center">
          <BaseIcon
            icon="qls-icon-arrow-back"
            class="cursor-pointer pl-1 pr-5 hover:text-primary-500 text-2xl font-extrabold"
            @click="navigateTo('/reports')"
          />
          <BaseTitle
            v-model:content="reportName"
            :editable="true"
          />
        </div>
        <div class="flex items-center">
          <BaseButton
            class="ml-2"
            text="Save"
            @click="handleSaveReportBtnClick"
          />
          <BaseButton
            class="ml-2"
            text="Download"
            @click="handleDownloadReportBtnClick"
          />
        </div>
      </div>
    </BaseHeader>
    <BaseContentWrapper>
      <div class="shadow-md pt-2 pb-2 pr-4 pl-4 mb-2">
        <div class="my-4">
          <ReportDetailsDescription
            :presetDescription="description"
            @change="handleDescriptionValueChange($event)"
          />
        </div>
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center flex-wrap">
            <template
              v-for="campaignOption in campaignOptions"
              :key="campaignOption.key"
            >
              <div
                :class="{
                  'border-purple-600': campaignOption.isActive,
                  'border-gray-300': !campaignOption.isActive,
                }"
                class="h-10 px-3 py-2 text-base rounded-md min-w-32 flex items-center justify-center border border-solid cursor-pointer mr-2 mb-1"
                @click="handleCampaignOptionClick(campaignOption)"
              >
                <svg
                  height="24"
                  width="40"
                >
                  <polyline
                    :class="{
                      'stroke-gray-400': !campaignOption.isActive,
                      'stroke-purple-600': campaignOption.isActive,
                    }"
                    :stroke-dasharray="campaignOption.strokeDashArray"
                    class="fill-none stroke-2"
                    points="0,12 8,0 16,24 24,0 32,24 40,0"
                  />
                </svg>
                <span class="pl-2">{{ campaignOption.name }}</span>
              </div>
            </template>
            <BaseButton
              text="Select campaigns"
              btn-style="outlined"
              icon="qls-icon-edit"
              class="mr-2 mb-1"
              @click="handleCampaignSelectBtnClick"
            />
          </div>
          <div>
            <ScenarioDropdown
              v-if="scenarioNames.length > 0"
              :scenarioNames="scenarioNames"
              :selectedScenarioNames="selectedScenarioNames"
              @scenarioChange="handleScenarioChange($event)"
            />
          </div>
        </div>
        <template
          v-for="activeCampaignOption in activeCampaignOptions"
          :key="activeCampaignOption.key"
        >
          <div
            v-if="activeCampaignOption.isActive && activeCampaignOption.enrichedScenarioReports.length > 0"
            class="px-6 py-2 bg-gray-50 dark:bg-gray-950 rounded-md mb-4"
          >
            <div class="flex items-center h-8 text-xl font-medium">
              <span>{{ activeCampaignOption.name }}</span>
            </div>
            <div class="flex items-center">
              <ScenarioDetails
                :scenario-reports="activeCampaignOption.enrichedScenarioReports"
                :status="activeCampaignOption.status"
              />
            </div>
          </div>
        </template>
      </div>
      <div class="shadow-md pt-2 pb-2 pr-4 pl-4">
        <div class="flex items-center">
          <BaseButton
            text="Add chart"
            icon="qls-icon-plus"
            btn-style="outlined"
            @click="handleAddChartBtnClick"
          />
          <BaseButton
            text="Add table"
            class="ml-4"
            icon="qls-icon-plus"
            btn-style="outlined"
            @click="handleAddTableBtnClick"
          />
        </div>
        <div class="pt-2 pb-2 pr-4 pl-4">
          <div
            v-for="(dataComponent, idx) in dataComponents"
            :key="dataComponent.id"
          >
            <template v-if="dataComponent.type === DataComponentTypeConstant.DATA_TABLE">
              <ReportDetailsTableData
                :dataSeries="dataComponent.datas"
                :component-index="idx"
              />
            </template>
            <template v-if="dataComponent.type === DataComponentTypeConstant.DIAGRAM">
              <ReportDetailsChartData
                :dataSeries="dataComponent.datas"
                :component-index="idx"
              />
            </template>
          </div>
        </div>
      </div>
      <ReportDetailsCampaignSelectDrawer
        v-if="campaignSelectDrawerOpen"
        v-model:open="campaignSelectDrawerOpen"
        :campaignPatterns="reportDetails?.campaignNamesPatterns"
        :description="reportDetails?.description"
        :report="reportDetails"
        @saved="handleReportUpdated"
      />
    </BaseContentWrapper>
    <BaseModal
      title="Changes are not yet saved"
      v-model:open="modalOpen"
      @confirmBtnClick="handleConfirmBtnClick"
    >
      <span>You have unsaved changes for the report. Do you really want to leave the page without saving?</span>
    </BaseModal>
  </template>
</template>

<script setup lang="ts">
const { fetchReportDetails, updateReport, downloadReport } = useReportApi()
const { fetchMultipleCampaignsDetails } = useCampaignApi()
const { fetchAllDataSeries, storeAllDataSeriesToCache } = useDataSeriesApi()

const route = useRoute()
const toastStore = useToastStore()
const reportDetailsStore = useReportDetailsStore()
const isReady = ref(false)

const {
  reportName,
  campaignOptions,
  scenarioNames,
  selectedScenarioNames,
  dataComponents,
  reportDetails,
  description,
} = storeToRefs(reportDetailsStore)

const activeCampaignOptions = computed(() => campaignOptions.value.filter((campaignOption) => campaignOption.isActive))
const campaignSelectDrawerOpen = ref(false)

const { modalOpen, confirmDiscard, markSaved } = useUnsavedChanges(() => reportDetailsStore.hasUnsavedChanges)

onMounted(async () => {
  await _fetchReport()
})

onBeforeUnmount(() => {
  reportDetailsStore.$reset()
})

const handleConfirmBtnClick = () => {
  confirmDiscard()
}

const _fetchReport = async () => {
  try {
    isReady.value = false
    const reportReference = route.params.id as string
    const reportDetails: DataReport = await fetchReportDetails(reportReference)
    let campaignOptions: CampaignOption[] = []
    const campaignKeys = reportDetails.resolvedCampaigns?.length
      ? reportDetails.resolvedCampaigns.map((campaign) => campaign.key)
      : []
    const campaigns: CampaignExecutionDetails[] = reportDetails.resolvedCampaigns
      ? await fetchMultipleCampaignsDetails(campaignKeys)
      : []
    const allDataSeries: DataSeries[] = await fetchAllDataSeries()
    storeAllDataSeriesToCache(allDataSeries)
    const allDataSeriesOptions: DataSeriesOption[] = SeriesHelper.toDataSeriesOptions(allDataSeries, [])

    campaignOptions = campaigns
      ? campaigns.map((campaignDetail, index) => ({
          ...campaignDetail,
          enrichedScenarioReports: ScenarioHelper.getSelectedScenarioReports(
            reportDetails.resolvedScenarioNames!,
            campaignDetail,
          ),
          strokeDashArray: index + 1,
          isActive: true,
        }))
      : []
    reportDetailsStore.$patch({
      reportDetails: reportDetails,
      reportName: reportDetails.displayName,
      campaignOptions: campaignOptions,
      dataComponents: structuredClone(reportDetails.dataComponents),
      scenarioNames: reportDetails.resolvedScenarioNames ? [...reportDetails.resolvedScenarioNames] : [],
      selectedScenarioNames: reportDetails.resolvedScenarioNames ? [...reportDetails.resolvedScenarioNames] : [],
      campaignNamesPatterns: reportDetails.campaignNamesPatterns ?? [],
      description: reportDetails.description ?? '',
      campaignKeys: reportDetails.resolvedCampaigns?.map((c) => c.key),
      allDataSeriesOptions: allDataSeriesOptions,
    })
    isReady.value = true
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleSaveReportBtnClick = async () => {
  const request: ReportCreationAndUpdateRequest = {
    displayName: reportDetailsStore.reportName,
    campaignKeys: reportDetailsStore.campaignKeys,
    campaignNamesPatterns: reportDetailsStore.campaignNamesPatterns,
    description: reportDetailsStore.description,
    sharingMode: SharingModeConstant.WRITE,
    scenarioNamesPatterns: reportDetailsStore.selectedScenarioNames,
    dataComponents: reportDetailsStore.dataComponents
      ? reportDetailsStore.dataComponents.map((dataComponent) => ({
          dataSeriesReferences: dataComponent.datas.map((d) => d.reference),
          type: dataComponent.type,
        }))
      : [],
  }
  try {
    await updateReport(reportDetailsStore.reportDetails!.reference, request)
    toastStore.success({ text: `Report ${reportDetailsStore.reportName} has been successfully updated.` })
    markSaved()
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleDownloadReportBtnClick = async () => {
  const reportReference = reportDetailsStore.reportDetails!.reference
  try {
    await downloadReport(reportReference)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleDescriptionValueChange = (value: string) => {
  reportDetailsStore.$patch({ description: value })
}

const handleAddChartBtnClick = () => {
  _addDataComponent(DataComponentTypeConstant.DIAGRAM)
}

const handleAddTableBtnClick = () => {
  _addDataComponent(DataComponentTypeConstant.DATA_TABLE)
}

const handleCampaignOptionClick = (campaignOption: CampaignOption) => {
  campaignOption.isActive = !campaignOption.isActive
}

const handleScenarioChange = (selectedNames: string[]) => {
  selectedScenarioNames.value = selectedNames
  reportDetailsStore.$patch({
    campaignOptions: campaignOptions.value.map((campaignOption) => ({
      ...campaignOption,
      enrichedScenarioReports: ScenarioHelper.getSelectedScenarioReports(selectedNames, campaignOption),
    })),
  })
}

const handleCampaignSelectBtnClick = () => {
  campaignSelectDrawerOpen.value = true
}

const handleReportUpdated = () => {
  markSaved()
  _fetchReport()
}

let _nextDataComponentId = 0
const _addDataComponent = (dataComponentType: DataComponentType) => {
  const dataComponent: DataComponent = {
    id: ++_nextDataComponentId,
    datas: [],
    type: dataComponentType,
  }
  const existingDataComponents = dataComponents?.value ?? []
  reportDetailsStore.$patch({
    dataComponents: [dataComponent, ...existingDataComponents],
  })
}
</script>
