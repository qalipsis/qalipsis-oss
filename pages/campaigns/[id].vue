<template>
  <div v-if="isPageReady && campaignDetails">
    <CampaignDetailsHeader :campaignDetails="campaignDetails" />
    <CampaignDetailsContent :campaignDetails="campaignDetails" />
  </div>
</template>

<script setup lang="ts">
const { fetchCampaignDetails } = useCampaignApi()
const { fetchAllDataSeries, storeAllDataSeriesToCache } = useDataSeriesApi()

const campaignDetailsStore = useCampaignDetailsStore()
const toastStore = useToastStore()

const route = useRoute()
const campaignDetails = ref<CampaignExecutionDetails>()
const isPageReady = ref(false)
const polling = ref()

onMounted(async () => {
  // Fetches the campaign details.
  await _fetchCampaignDetails()

  // Fetches all data series.
  const allDataSeries = (await fetchAllDataSeries()).filter(
    (d) => d.reference !== SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
  )
  storeAllDataSeriesToCache(allDataSeries)

  // The selected scenario names from the url query params.
  const scenarioNames = route.query?.scenarios?.toString().split(',')

  // The min and max date time with unix time format from the url query params.
  const min = route.query?.min?.toString()
  const max = route.query?.max?.toString()

  // Updates the selected scenario, series, time min max from the campaign details store
  campaignDetailsStore.$patch({
    campaignDetails: campaignDetails.value,
    selectedScenarioNames:
      scenarioNames && scenarioNames?.length > 0 ? scenarioNames : campaignDetails.value?.scenarios?.map((s) => s.name),
    timeRange: {
      min,
      max,
    },
  })

  // The selected series references from the url query params.
  const dataSeriesReferences = route.query?.series?.toString().split(',')

  if (dataSeriesReferences && dataSeriesReferences.length > 0) {
    const selectedDataSeries = allDataSeries.filter((d) => dataSeriesReferences.includes(d.reference))
    campaignDetailsStore.$patch({
      selectedDataSeries: selectedDataSeries,
    })
  }

  // Triggers the refresh interval when the status is IN_PROGRESS.
  if (campaignDetails.value?.status === 'IN_PROGRESS') {
    _triggerRefreshInterval()
  }

  isPageReady.value = true
})

onBeforeUnmount(() => {
  campaignDetailsStore.$reset()
  if (polling.value) {
    clearInterval(polling.value)
  }
})

const _triggerRefreshInterval = () => {
  // Keep refreshing the campaign details every 5 seconds.
  polling.value = setInterval(async () => {
    await _fetchCampaignDetails()

    // Updates the campaign details store.
    campaignDetailsStore.$patch({
      campaignDetails: campaignDetails.value,
      selectedScenarioNames: campaignDetails.value?.scenarios
        ? campaignDetails.value?.scenarios?.map((s) => s.name)
        : [],
    })

    // Remove the interval when the status is not IN_PROGRESS anymore.
    if (polling.value && campaignDetails.value?.status !== 'IN_PROGRESS') {
      clearInterval(polling.value)
    }
  }, 5000)
}

const _fetchCampaignDetails = async () => {
  try {
    const campaignKey = route.params.id as string
    campaignDetails.value = await fetchCampaignDetails(campaignKey)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
