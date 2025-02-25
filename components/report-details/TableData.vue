<template>
    <section class="group mb-4">
        <div class="flex justify-end mb-2 invisible transition-all ease-in-out duration-300 group-hover:visible">
            <BaseButton icon="qls-icon-delete" text="Delete" btn-style="outlined" @click="handleDeleteBtnClick"/>
        </div>
        <SeriesMenu 
            :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
            @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)" />
        <BaseTable
            :data-source="tableData"
            :total-elements="tableData.length"
            :table-column-configs="ReportDetailsConfig.TABLE_COLUMNS"
            :page-size="TableHelper.defaultPageSize"
            :current-page-index="currentPageIndex"
            :all-data-source-included="true"
            :refresh-hidden="true"
            @page-change="handlePaginationChange"
            row-key="id"
        ></BaseTable>
    </section>
</template>

<script setup lang="ts">

const props = defineProps<{
    componentIndex: number,
    dataSeries: DataSeries[],
}>();

const reportDetailsStore = useReportDetailsStore();
const { fetchTimeSeriesAggregation } = useTimeSeriesApi();

const tableData = ref<ReportDetailsTableData[]>([]);
const preselectedDataSeriesReferences = ref<string[]>([]);
const currentPageIndex = ref<number>(0);

let selectedDataSeriesOptions: DataSeriesOption[] = [];

onMounted(() => {
    preselectedDataSeriesReferences.value = props.dataSeries.map(dataSeries => dataSeries.reference);
    selectedDataSeriesOptions = SeriesHelper.toDataSeriesOptions(props.dataSeries, []);
    _updateTableData(selectedDataSeriesOptions)
})

watch(() => [reportDetailsStore.activeCampaignOptions, reportDetailsStore.selectedScenarioNames], () => {
    _updateTableData(selectedDataSeriesOptions);
})

const handlePaginationChange = (pageIndex: number) => {
    currentPageIndex.value = pageIndex
}

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
