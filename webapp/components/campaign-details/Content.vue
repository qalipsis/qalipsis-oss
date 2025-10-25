<template>
  <BaseContentWrapper>
    <BaseCard>
      <div class="flex justify-between">
        <ScenarioDetails :scenarioReports="scenarioReports" />
        <template v-if="campaignDetails.status === 'IN_PROGRESS'">
          <BasePermission :permissions="[PermissionConstant.WRITE_CAMPAIGN]">
            <BaseButton
              text="Stop all"
              @click="campaignStopModalOpen = true"
            />
          </BasePermission>
        </template>
      </div>
    </BaseCard>
    <div class="shadow-md pt-2 pb-2 pr-4 pl-4 mb-2">
      <SeriesMenu
        :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
        @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)"
      />
      <div class="mt-10">
        <apexchart
          v-if="!isUpdatingChart"
          :options="chartOptions"
          :series="chartDataSeries"
          :height="460"
          @zoomed="handleZoom"
        />
        <div
          v-if="isUpdatingChart"
          class="h-[460px] bg-white dark:bg-primary-900"
        ></div>
      </div>
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
          text="Soft"
          @click="campaignStopModalOpen = false"
        />
        <BaseButton
          text="Abort"
          @click="handleAbortButtonClick"
        />
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
defineProps<{
  campaignDetails: CampaignExecutionDetails
}>()

const route = useRoute()
const router = useRouter()
const campaignDetailsStore = useCampaignDetailsStore()
const toastStore = useToastStore()

const { chartOptions, chartDataSeries } = storeToRefs(campaignDetailsStore)

const campaignStopModalOpen = ref(false)
const scenarioReports = computed(() =>
  ScenarioHelper.getSelectedScenarioReports(
    campaignDetailsStore.selectedScenarioNames,
    campaignDetailsStore.campaignDetails!
  )
)
const campaignDetailStatus = computed(() => campaignDetailsStore.campaignDetails!.status)
const preselectedDataSeriesReferences = computed(() => campaignDetailsStore.selectedDataSeriesReferences)
const { abortCampaign, fetchCampaignDetails } = useCampaignApi()

const isUpdatingChart = ref(false)

onMounted(() => {
  _updateChart()
})

const _updateChart = async () => {
  try {
    isUpdatingChart.value = true
    await campaignDetailsStore.updateChart()
    isUpdatingChart.value = false

    if (campaignDetailStatus.value === 'IN_PROGRESS') {
      setTimeout(_updateChart, 5000)
    }
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    isUpdatingChart.value = false
  }
}

const handleSelectedDataSeriesChange = async (selectedDataSeriesOptions: DataSeriesOption[]) => {
  // Updates url param
  if (selectedDataSeriesOptions.length === 0) {
    // Removes series query params from the url.
    const query = Object.assign({}, route.query)
    delete query.series
    router.replace({
      query: query,
    })
  } else {
    // Sets the selected series option id list to the url query params.
    const seriesReferences = selectedDataSeriesOptions.map((dataSeries) => dataSeries.reference).join(',')
    const currentQueryParams = route.query
    const newQueryParams = {
      ...currentQueryParams,
      series: seriesReferences,
    }
    router.push({
      query: newQueryParams,
    })
  }

  // Updates the store
  campaignDetailsStore.$patch({
    selectedDataSeries: selectedDataSeriesOptions,
  })

  // Updates the chart
  _updateChart()
}

/**
 * Sets the selected min and max time from the zoomed event to the url query params.
 *
 * @param {Object} _ - The first parameter is ignored.
 * @param {Object} xaxis - The x-axis object containing the minimum and maximum values in unix time format.
 *
 * @see https://apexcharts.com/docs/options/chart/events/#zoomed
 */
const handleZoom = (_: any, { xaxis }: any) => {
  const currentQueryParams = route.query
  const newQueryParams = {
    ...currentQueryParams,
    min: xaxis.min,
    max: xaxis.max,
  }
  // Updates the url query params
  router.push({
    query: newQueryParams,
  })
  // Updates the store
  campaignDetailsStore.$patch({
    timeRange: {
      min: xaxis.min,
      max: xaxis.max,
    },
  })
}

const handleAbortButtonClick = () => {
  _stopCampaign(true)
}

/**
 * Stops the campaign
 */
const _stopCampaign = async (isForceAbort: boolean) => {
  try {
    await abortCampaign(campaignDetailsStore.campaignDetails!.key, isForceAbort)
    const campaignDetails = await fetchCampaignDetails(campaignDetailsStore.campaignDetails!.key)
    campaignDetailsStore.$patch({
      campaignDetails: campaignDetails,
    })
    campaignStopModalOpen.value = false
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
