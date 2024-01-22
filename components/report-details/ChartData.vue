<template>
    <section class="report-details-data-component mb-4">
        <div class="flex content-end mb-2 delete-btn-wrapper">
            <BaseButton icon="/icons/icon-delete-small.svg" text="Delete" btnStyle="stroke" @click="handleDeleteBtnClick"/>
        </div>
        <SeriesMenu 
            :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
            @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)"
        />
        <div class="pt-8">
            <apexchart
                v-if="chartOptions && !isLoadingChart"
                :options="chartOptions"
                :series="chartDataSeries"
                :height="460"
            />
        </div>
    </section>
</template>

<script setup lang="ts">
import { ApexOptions } from 'apexcharts';

const props = defineProps<{
    componentIndex: number,
    dataSeries: DataSeries[],
}>();

const reportDetailsStore = useReportDetailsStore();
const { fetchTimeSeriesAggregation } = useTimeSeriesApi();

const chartOptions = ref<ApexOptions>();
const chartDataSeries = ref<ApexAxisChartSeries>();
const isLoadingChart = ref(false);
const preselectedDataSeriesReferences = ref<string[]>([]);

let selectedDataSeriesOptions: DataSeriesOption[] = [];

onMounted(() => {
    preselectedDataSeriesReferences.value = props.dataSeries.map(dataSeries => dataSeries.reference);
    selectedDataSeriesOptions = SeriesHelper.toDataSeriesOptions(props.dataSeries, []);
    _updateChartData(selectedDataSeriesOptions)
})

watch(() => [reportDetailsStore.activeCampaignOptions, reportDetailsStore.selectedScenarioNames], () => {
    _updateChartData(selectedDataSeriesOptions);
})

const handleDeleteBtnClick = () => {
    reportDetailsStore.deleteDataComponent(props.componentIndex);
}

const handleSelectedDataSeriesChange = (dataSeriesOptions: DataSeriesOption[]) => {
    selectedDataSeriesOptions = dataSeriesOptions;
    const dataComponents = [...reportDetailsStore.dataComponents];
    dataComponents[props.componentIndex].datas = dataSeriesOptions;
    reportDetailsStore.$patch({
        dataComponents: dataComponents
    });
    _updateChartData(dataSeriesOptions);
}

const _updateChartData = async (dataSeriesOptions: DataSeriesOption[]) => {
    const selectedDataSeriesReferences = dataSeriesOptions.map(dataSeriesOption => dataSeriesOption.reference);
    // Note: always add the minions count data series reference for querying the time series data
    const seriesReferences = [SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE, ...selectedDataSeriesReferences].join(",")
    const queryParam: TimeSeriesAggregationQueryParam = {
        series: seriesReferences,
        scenarios: reportDetailsStore.selectedScenarioNames.join(','),
        campaigns: reportDetailsStore.activeCampaignOptions.map(campaignOption => campaignOption.key).join(',')
    }
    try {
        isLoadingChart.value = true;
        const timeSeriesAggregationResult: { [key: string]: TimeSeriesAggregationResult[] } = await fetchTimeSeriesAggregation(queryParam);
        const chartData: ChartData = ReportHelper.toReportChartData(timeSeriesAggregationResult, dataSeriesOptions, reportDetailsStore.activeCampaignOptions);
        chartOptions.value = chartData.chartOptions;
        chartDataSeries.value = chartData.chartDataSeries;
    } catch (error) {
        console.error(error);
    }
    isLoadingChart.value = false;
}

</script>
