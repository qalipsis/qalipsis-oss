<template>
  <div
    v-if="isPageReady && campaignDetails"
    class="h-full flex flex-col"
  >
    <BaseHeader>
      <div class="flex items-center w-full justify-between">
        <div class="flex items-center">
          <BaseIcon
            icon="qls-icon-arrow-back"
            class="cursor-pointer pl-1 pr-5 hover:text-primary-500 text-2xl"
            @click="navigateTo('/campaigns')"
          />
          <BaseTitle v-model:content="name" />
        </div>
        <div class="flex items-center gap-x-2">
          <ScenarioDropdown
            v-if="scenarioNames.length > 0"
            :scenarioNames="scenarioNames"
            :selectedScenarioNames="selectedScenarioNames"
            @scenarioChange="handleScenarioChange($event)"
          />
          <template v-if="campaignDetails.status === 'IN_PROGRESS'">
            <BasePermission :permissions="[writeCampaignPermission]">
              <BaseButton
                text="Stop all"
                theme="error"
                @click="campaignStopModalOpen = true"
              />
            </BasePermission>
          </template>
          <template v-if="!['IN_PROGRESS', 'QUEUED', 'SCHEDULED'].includes(campaignDetails.status)">
            <BaseButton
                text="Download report"
                :loading="isDownloadingReport"
                @click="handleDownloadReport"
            />
          </template>
          <template v-if="campaignDetails.zones && campaignDetails.zones.length > 0">
            <div
              v-if="firstZoneKey && zoneKeyToZoneModel[firstZoneKey]"
              class="flex items-center gap-x-2"
            >
              <img
                v-if="zoneKeyToZoneModel[firstZoneKey]?.imagePath"
                :src="zoneKeyToZoneModel[firstZoneKey]?.imagePath"
                class="rounded-full bg-cover w-5 h-5"
              />
              <span class="text-gray-600">
                {{ zoneKeyToZoneModel[firstZoneKey]?.title }}
              </span>
              <BaseTooltip v-if="campaignDetails.zones.length > 0">
                <template #tooltipContent>
                  <div
                    v-for="(zoneKey, _) in campaignDetails.zones"
                    :key="zoneKey"
                    class="flex items-center gap-x-2"
                  >
                    <img
                      v-if="zoneKeyToZoneModel[zoneKey]?.imagePath"
                      :src="zoneKeyToZoneModel[zoneKey].imagePath"
                      class="rounded-full bg-cover w-5 h-5"
                    />
                    <div>
                      <div class="text-white">
                        {{ zoneKeyToZoneModel[zoneKey]?.title }}
                      </div>
                      <div
                        v-if="zoneKeyToZoneModel[zoneKey]?.description"
                        class="text-gray-200"
                      >
                        {{ zoneKeyToZoneModel[zoneKey]?.description }}
                      </div>
                    </div>
                  </div>
                </template>
                <span class="px-2 bg-gray-50 rounded-lg"> +&nbsp;{{ campaignDetails.zones.length - 1 }} </span>
              </BaseTooltip>
            </div>
          </template>
        </div>
      </div>
    </BaseHeader>
    <BaseContentWrapper>
      <div class="shrink-0">
        <BaseCard>
          <ScenarioDetails
            :scenarioReports="scenarioReports"
            :status="campaignDetails.status"
            :zones="zoneKeyToZoneModel"
          />
        </BaseCard>
      </div>
      <div class="shadow-md py-4 px-4 flex items-stretch gap-x-4 flex-1 min-h-0">
        <template v-if="hasDataSeries === false">
          <div class="flex flex-col items-center justify-center flex-1 gap-y-4 text-center px-8">
            <BaseIcon icon="qls-icon-chart-bar" class="text-5xl text-gray-300 dark:text-gray-600"/>
            <p class="text-gray-500 dark:text-gray-400 text-sm max-w-sm">
              To graphically visualise your campaign data, you need to create at least one data series.
              Data series define the metrics and events to render and how to display them on the chart.
            </p>
            <BaseButton
                text="Create data series"
                @click="navigateTo('/series')"
            />
          </div>
        </template>
        <template v-else>
          <div class="flex-1 min-h-[600px] h-full py-2">
            <apexchart
                v-if="!isUpdatingChart"
                height="100%"
                :options="chartOptions"
                :series="chartDataSeries"
                @zoomed="handleZoom"
            />
            <div
                v-if="isUpdatingChart"
                class="h-full bg-white dark:bg-primary-900"
            ></div>
          </div>
          <div class="w-72 py-2 h-full">
            <SeriesPanel
                :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
                :campaignKey="campaignDetails.key"
                :maximum="8"
                @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)"
            />
          </div>
        </template>
      </div>
    </BaseContentWrapper>
    <BaseModal
      title="Stop campaign"
      :open="campaignStopModalOpen"
      :closable="true"
      @close="campaignStopModalOpen = false"
    >
      <span>Do you really want to abort this campaign? This will cause all running scenarios to fail.</span>
      <template #customFooter>
        <div class="flex items-center justify-around">
          <BaseButton
            btn-style="outlined"
            theme="error"
            text="Cancel"
            @click="campaignStopModalOpen = false"
          />
          <BaseButton
            text="Proceed"
            theme="error"
            @click="handleAbortButtonClick"
          />
        </div>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
const {fetchCampaignDetails, abortCampaign, downloadHtmlReport} = useCampaignApi()
const { getCachedDataSeries } = useDataSeriesApi()
const { fetchZones } = useZonesApi()

const campaignDetailsStore = useCampaignDetailsStore()
const toastStore = useToastStore()

const route = useRoute()
const router = useRouter()

const writeCampaignPermission = PermissionConstant.WRITE_CAMPAIGN

const { selectedScenarioNames, chartOptions, chartDataSeries } = storeToRefs(campaignDetailsStore)

const campaignDetails = ref<CampaignExecutionDetails>()
const isPageReady = ref(false)
const name = ref('')
const scenarioNames = ref<string[]>([])
const campaignStopModalOpen = ref(false)
const isDownloadingReport = ref(false)
const zoneKeyToZoneModel = ref<{ [key: string]: Zone }>({})
const hasDataSeries = ref<boolean | null>(null)

const scenarioReports = computed(() => {
  if (!campaignDetailsStore.campaignDetails) return []

  return ScenarioHelper.getSelectedScenarioReports(
    campaignDetailsStore.selectedScenarioNames,
    campaignDetailsStore.campaignDetails,
  )
})
const preselectedDataSeriesReferences = computed(() => campaignDetailsStore.selectedDataSeriesReferences)
const firstZoneKey = computed(() => campaignDetails.value?.zones?.[0] ?? '')

const isUpdatingChart = ref(false)

const { run: _runPollingCycle } = usePolling(
  async () => {
    await _fetchCampaignDetails()
    campaignDetailsStore.$patch({
      campaignDetails: campaignDetails.value,
      selectedScenarioNames: campaignDetails.value?.scenarios?.map((s) => s.name) ?? [],
    })
    if (hasDataSeries.value) {
      isUpdatingChart.value = true
      try {
        await campaignDetailsStore.updateChart()
      } finally {
        isUpdatingChart.value = false
      }
    }
  },
  () => campaignDetails.value?.status === 'IN_PROGRESS',
    5000,
    false,
)

onMounted(async () => {
  await _fetchCampaignDetails()

  const scenarioNamesFromQuery = route.query?.scenarios ? String(route.query.scenarios).split(',').filter(Boolean) : []
  const min = route.query?.min?.toString()
  const max = route.query?.max?.toString()

  campaignDetailsStore.$patch({
    campaignDetails: campaignDetails.value,
    selectedScenarioNames:
      scenarioNamesFromQuery.length > 0 ? scenarioNamesFromQuery : campaignDetails.value?.scenarios?.map((s) => s.name),
    timeRange: { min, max },
  })

  const allCampaignDataSeries = (await getCachedDataSeries({campaign: campaignDetails.value?.key})).filter(
      (d) => d.reference !== SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE,
  )
  hasDataSeries.value = allCampaignDataSeries.length > 0

  const dataSeriesReferences = route.query?.series ? String(route.query.series).split(',').filter(Boolean) : []
  if (dataSeriesReferences.length > 0) {
    const selectedDataSeries = allCampaignDataSeries.filter((d) => dataSeriesReferences.includes(d.reference))
    campaignDetailsStore.$patch({ selectedDataSeries })
  }

  name.value = campaignDetails.value?.name ?? ''
  scenarioNames.value = campaignDetails.value?.scenarios?.map((s) => s.name) ?? []

  isUpdatingChart.value = true
  isPageReady.value = true

  _runPollingCycle()
  _setZoneKeyToModel()

  if (hasDataSeries.value) {
    try {
      await campaignDetailsStore.updateChart()
    } finally {
      isUpdatingChart.value = false
    }
  } else {
    isUpdatingChart.value = false
  }
})

onBeforeUnmount(() => {
  campaignDetailsStore.$reset()
})

const _fetchCampaignDetails = async () => {
  try {
    const campaignKey = route.params.id as string
    campaignDetails.value = await fetchCampaignDetails(campaignKey)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const _setZoneKeyToModel = async () => {
  const zones = await fetchZones()
  zoneKeyToZoneModel.value = keyBy(zones, 'key')
}

const handleScenarioChange = async (names: string[]) => {
  campaignDetailsStore.$patch({ selectedScenarioNames: names })
  const currentQueryParams = route.query
  const newQueryParams = { ...currentQueryParams, scenarios: names.join(',') }
  router.replace({ query: newQueryParams })
  try {
    await campaignDetailsStore.updateChart()
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleSelectedDataSeriesChange = async (selectedDataSeriesOptions: DataSeriesOption[]) => {
  if (selectedDataSeriesOptions.length === 0) {
    const query = {...route.query}
    delete query.series
    router.replace({ query })
  } else {
    const seriesReferences = selectedDataSeriesOptions.map((dataSeries) => dataSeries.reference).join(',')
    const currentQueryParams = route.query
    const newQueryParams = { ...currentQueryParams, series: seriesReferences }
    router.push({ query: newQueryParams })
  }
  campaignDetailsStore.$patch({ selectedDataSeries: selectedDataSeriesOptions })
  isUpdatingChart.value = true
  try {
    await campaignDetailsStore.updateChart()
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  } finally {
    isUpdatingChart.value = false
  }
}

/**
 * Sets the selected min and max time from the zoomed event to the url query params.
 * Debounced to avoid spamming router.push during drag-zoom.
 *
 * @param {Object} _ - The first parameter is ignored.
 * @param {Object} xaxis - The x-axis object containing the minimum and maximum values in unix time format.
 *
 * @see https://apexcharts.com/docs/options/chart/events/#zoomed
 */
const handleZoom = debounce((_: any, { xaxis }: any) => {
  const currentQueryParams = route.query
  const newQueryParams = { ...currentQueryParams, min: xaxis.min, max: xaxis.max }
  router.push({ query: newQueryParams })
  campaignDetailsStore.$patch({ timeRange: { min: xaxis.min, max: xaxis.max } })
}, 400)

const handleDownloadReport = async () => {
  isDownloadingReport.value = true
  try {
    await downloadHtmlReport(campaignDetails.value!.key)
  } catch (error) {
    toastStore.error({text: ErrorHelper.getErrorMessage(error)})
  } finally {
    isDownloadingReport.value = false
  }
}

const handleAbortButtonClick = () => {
  _stopCampaign(true)
}

const _stopCampaign = async (isForceAbort: boolean) => {
  try {
    await abortCampaign(campaignDetailsStore.campaignDetails!.key, isForceAbort)
    const updatedDetails = await fetchCampaignDetails(campaignDetailsStore.campaignDetails!.key)
    campaignDetailsStore.$patch({ campaignDetails: updatedDetails })
    campaignStopModalOpen.value = false
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
