<template>
  <section class="group mb-4">
    <div class="flex justify-end mb-2 invisible transition-all ease-in-out duration-300 group-hover:visible">
      <BaseButton
        icon="qls-icon-delete"
        text="Delete"
        btn-style="outlined"
        @click="handleDeleteBtnClick"
      />
    </div>
    <div class="flex">
      <div class="flex-1 min-h-[600px] h-full py-2">
        <apexchart
          v-if="chartOptions && !isLoadingChart"
          :options="chartOptions"
          :series="chartDataSeries"
          height="100%"
        />
      </div>
      <div class="w-72 py-2 h-full">
        <SeriesPanel
          :preselectedDataSeriesReferences="preselectedDataSeriesReferences"
          :maximum="8"
          @selectedDataSeriesChange="handleSelectedDataSeriesChange($event)"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type {ApexOptions} from 'apexcharts'

const props = defineProps<{
  componentIndex: number
  dataSeries: DataSeries[]
}>()

const reportDetailsStore = useReportDetailsStore()
const { fetchTimeSeriesAggregation } = useTimeSeriesApi()
const toastStore = useToastStore()

const chartOptions = ref<ApexOptions>()
const chartDataSeries = ref<ApexAxisChartSeries>()
const isLoadingChart = ref(false)
const preselectedDataSeriesReferences = ref<string[]>([])

let selectedDataSeriesOptions: DataSeriesOption[] = []

onMounted(() => {
  preselectedDataSeriesReferences.value = props.dataSeries.map((dataSeries) => dataSeries.reference)
  selectedDataSeriesOptions = SeriesHelper.toDataSeriesOptions(props.dataSeries, [])
  _updateChartData(selectedDataSeriesOptions)
})

watch(
  () => [reportDetailsStore.activeCampaignOptions, reportDetailsStore.selectedScenarioNames],
  () => {
    _updateChartData(selectedDataSeriesOptions)
  },
)

const handleDeleteBtnClick = () => {
  reportDetailsStore.deleteDataComponent(props.componentIndex)
}

const handleSelectedDataSeriesChange = (dataSeriesOptions: DataSeriesOption[]) => {
  selectedDataSeriesOptions = dataSeriesOptions
  const dataComponents = [...reportDetailsStore.dataComponents]
  const component = dataComponents[props.componentIndex]
  if (component) {
    component.datas = dataSeriesOptions
  }
  reportDetailsStore.$patch({
    dataComponents: dataComponents,
  })
  _updateChartData(dataSeriesOptions)
}

const _updateChartData = async (dataSeriesOptions: DataSeriesOption[]) => {
  const queryParam: TimeSeriesAggregationQueryParam = AggregationDataHelper.getTimeSeriesAggregationQueryParam({
    series: dataSeriesOptions.map((dataSeriesOption) => dataSeriesOption.reference),
    campaigns: reportDetailsStore.activeCampaignOptions.map((campaignOption) => campaignOption.key),
    selectedScenarios: reportDetailsStore.selectedScenarioNames,
    availableScenarios: reportDetailsStore.scenarioNames,
  })

  try {
    isLoadingChart.value = true
    const timeSeriesAggregationResult: {
      [key: string]: TimeSeriesValues
    } = await fetchTimeSeriesAggregation(queryParam)
    const chartData: ChartData = ReportHelper.toReportChartData(
      timeSeriesAggregationResult,
      dataSeriesOptions,
      reportDetailsStore.activeCampaignOptions,
    )
    chartOptions.value = chartData.chartOptions
    chartDataSeries.value = chartData.chartDataSeries
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  } finally {
    isLoadingChart.value = false
  }
}
</script>
