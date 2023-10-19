<template>
    <div v-if="isPageReady && campaignDetails">
        <CampaignDetailsHeader :campaignDetails="campaignDetails" />
        <CampaignDetailsContent :campaignDetails="campaignDetails" />
    </div>
</template>

<script setup lang="ts">

const { fetchCampaignDetails } = useCampaignApi();
const { fetchAllDataSeries } = useDataSeriesApi();

const campaignDetailsStore = useCampaignDetailsStore();
const userStore = useUserStore();

const route = useRoute();
const campaignDetails = ref<CampaignExecutionDetails>();
const isPageReady = ref(false);

onMounted(async () => {
    try {
        const campaignKey = route.params.id as string;
        campaignDetails.value = await fetchCampaignDetails(campaignKey);
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }

    // The selected scenario names from the url query params.
    const scenarioNames = route.query?.scenarios?.toString().split(',');

    // The min and max date time with unix time format from the url query params.
    const min = route.query?.min?.toString();
    const max = route.query?.max?.toString();
    
    // Updates the selected scenario, series, time min max from the campaign details store
    campaignDetailsStore.$patch({
        campaignDetails: campaignDetails.value,
        selectedScenarioNames: scenarioNames && scenarioNames?.length > 0 
            ? scenarioNames
            : campaignDetails.value?.scenarios?.map(s => s.name),
        timeRange: {
            min,
            max
        }
    })

    // The selected series references from the url query params.
    const dataSeriesReferences = route.query?.series?.toString().split(',');

    if (dataSeriesReferences && dataSeriesReferences.length > 0) {
        const allDataSeries = (await fetchAllDataSeries()).filter(d => d.reference !== SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE);
        const selectedDataSeries = allDataSeries.filter(d => dataSeriesReferences.includes(d.reference));
        campaignDetailsStore.$patch({
            selectedDataSeries: selectedDataSeries
        })
    }

    isPageReady.value = true;

})

watch(() => userStore.currentTenantReference, () => {
    navigateTo('/campaigns');
})

onBeforeUnmount(() => {
    campaignDetailsStore.$reset();
})

</script>

<style scoped></style>