<template>
    <section class="report-details-data-component mb-4">
        <div class="flex content-end mb-2 delete-btn-wrapper">
            <BaseButton icon="/icons/icon-delete-small.svg" text="Delete" btnStyle="stroke" @click="handleDeleteBtnClick"/>
        </div>
        <SeriesMenu 
            :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
            @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)" />
        <a-table 
            :data-source="tableData"
            :pagination="pagination"
            :columns="tableColumns"
            :show-sorter-tooltip="false"
            :ellipsis="true">
        </a-table>
    </section>
</template>

<script setup lang="ts">

const props = defineProps<{
    componentIndex: number,
    dataSeries: DataSeries[],
}>();

const reportDetailsStore = useReportDetailsStore();
const { fetchTimeSeriesAggregation } = useTimeSeriesApi();

const tableColumns = ReportDetailsConfig.TABLE_COLUMNS;
const tableData = ref<ReportDetailsTableData[]>([]);
const preselectedDataSeriesReferences = ref<string[]>([]);
const pagination = reactive({
    pageSize: TableHelper.defaultPageSize,
    total: tableData.value.length,
    ...TableHelper.sharedPaginationProperties
});

let selectedDataSeriesOptions: DataSeriesOption[] = [];

onMounted(() => {
    preselectedDataSeriesReferences.value = props.dataSeries.map(dataSeries => dataSeries.reference);
    selectedDataSeriesOptions = SeriesHelper.toDataSeriesOptions(props.dataSeries, []);
    _updateTableData(selectedDataSeriesOptions)
})

watch(() => [reportDetailsStore.activeCampaignOptions, reportDetailsStore.selectedScenarioNames], () => {
    _updateTableData(selectedDataSeriesOptions);
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
    })
    _updateTableData(dataSeriesOptions);
}

const _updateTableData = async (dataSeriesOptions: DataSeriesOption[]) => {
    const selectedDataSeriesReferences = dataSeriesOptions.map(dataSeriesOption => dataSeriesOption.reference);
    // Note: always add the minions count data series reference for querying the time series data
    const seriesReferences = [SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE, ...selectedDataSeriesReferences].join(",");
    const queryParam: TimeSeriesAggregationQueryParam = {
        series: seriesReferences,
        scenarios: reportDetailsStore.selectedScenarioNames.join(','),
        campaigns: reportDetailsStore.activeCampaignOptions.map(campaignOption => campaignOption.key).join(',')
    }
    try {
        const timeSeriesAggregationResult = await fetchTimeSeriesAggregation(queryParam);
        tableData.value = ReportHelper.toReportDetailsTableData(timeSeriesAggregationResult, dataSeriesOptions, reportDetailsStore.activeCampaignOptions);
    } catch (error) {
        console.error(error)
    }
}

</script>
