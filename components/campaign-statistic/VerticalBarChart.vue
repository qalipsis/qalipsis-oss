<template>
    <section class="vertical-bar-chart-section">
        <div class="vertical-bar-chart-header">
            <BaseIcon icon="/icons/icon-chart-bold-purple.svg" />
            <span class="pl-2">Number of tests per day</span>
        </div>
        <div class="chart-wrapper">
            <apexchart
                v-if="chartOptions"
                :options="chartOptions"
                :series="chartDataSeries"
                :height="200"
            />
        </div>
    </section>
</template>

<script setup lang="ts">
import { ApexOptions } from 'apexcharts';
import { sub } from 'date-fns';

const { fetchCampaignSummary } = useTimeSeriesApi();

const chartOptions = ref<ApexOptions>();
const chartDataSeries = ref<ApexAxisChartSeries>();

onMounted(async () => {
    const currentDate = new Date();
    const timezoneOffsetInHours = currentDate.getTimezoneOffset() / 60;
    let campaignSummary: CampaignSummaryResult[] = [];

    try {
        const queryParam: CampaignSummaryResultQueryParams = {
            from: sub(currentDate, { days: 7 }),
            timeframe: "P1D",
            timeOffset: timezoneOffsetInHours
        }
        campaignSummary = await fetchCampaignSummary(queryParam);
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
    
    const campaignSummaryChartData: ChartData = CampaignHelper.toCampaignSummaryChartData(campaignSummary);
    chartOptions.value = campaignSummaryChartData.chartOptions;
    chartDataSeries.value = campaignSummaryChartData.chartDataSeries;
})

</script>

<style scoped lang="scss">
.vertical-bar-chart-header {
    display: flex;
    align-items: center;
}

.chart-wrapper {
    padding: .5rem;
    width: 17.5rem;
}
</style>