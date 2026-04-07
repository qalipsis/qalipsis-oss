<template>
  <section class="group mb-4">
    <div class="flex justify-end mb-2 invisible transition-all ease-in-out duration-300 group-hover:visible">
      <BaseButton icon="qls-icon-delete" text="Delete" btn-style="outlined" @click="handleDeleteBtnClick"/>
    </div>
    <SeriesMenu
        :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
        @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)"/>
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
const {fetchTimeSeriesAggregation} = useTimeSeriesApi();
const toastStore = useToastStore();

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
  const component = dataComponents[props.componentIndex];
  if (component) {
    component.datas = dataSeriesOptions;
  }
  reportDetailsStore.$patch({
    dataComponents: dataComponents
  })
  _updateTableData(dataSeriesOptions);
}

const _updateTableData = async (dataSeriesOptions: DataSeriesOption[]) => {
  const queryParam: TimeSeriesAggregationQueryParam = AggregationDataHelper.getTimeSeriesAggregationQueryParam({
    series: dataSeriesOptions.map(dataSeriesOption => dataSeriesOption.reference),
    campaigns: reportDetailsStore.activeCampaignOptions.map(campaignOption => campaignOption.key),
    selectedScenarios: reportDetailsStore.selectedScenarioNames,
    availableScenarios: reportDetailsStore.scenarioNames
  })

  try {
    const timeSeriesAggregationResult = await fetchTimeSeriesAggregation(queryParam);
    tableData.value = ReportHelper.toReportDetailsTableData(timeSeriesAggregationResult, dataSeriesOptions, reportDetailsStore.activeCampaignOptions);
  } catch (error) {
    toastStore.error({text: ErrorHelper.getErrorMessage(error)});
  }
}

</script>
