import type {ApexOptions} from 'apexcharts'

interface CampaignDetailsStoreState {
  campaignDetails: CampaignExecutionDetails | null
  selectedScenarioNames: string[]
  selectedDataSeries: DataSeries[]
  timeRange: {
    max: string
    min: string
  }
  chartOptions: ApexOptions | null
  chartDataSeries: ApexAxisChartSeries
  isLoadingChart: boolean
}

export const useCampaignDetailsStore = defineStore('CampaignDetails', {
  state: (): CampaignDetailsStoreState => {
    return {
      campaignDetails: null,
      selectedScenarioNames: [],
      selectedDataSeries: [],
      timeRange: {
        min: '',
        max: '',
      },
      chartOptions: null,
      chartDataSeries: [],
      isLoadingChart: false,
    }
  },
  getters: {
    selectedDataSeriesReferences: (state) =>
      state.selectedDataSeries.map((dataSeries: DataSeries) => dataSeries.reference),
  },
  actions: {
    async updateChart(): Promise<void> {
      const campaignDetails = this.campaignDetails
      if (!campaignDetails) return

      // Prepares the query params.
      const queryParams: TimeSeriesAggregationQueryParam = AggregationDataHelper.getTimeSeriesAggregationQueryParam({
        campaigns: [campaignDetails.key],
        series: this.selectedDataSeriesReferences,
        selectedScenarios: this.selectedScenarioNames,
          availableScenarios: campaignDetails.scenarios?.map((s) => s.name) ?? [],
        from: campaignDetails.start,
        until: campaignDetails.end,
      })

      // Fetches the data from the time series service.
      this.isLoadingChart = true
      const { fetchTimeSeriesAggregation } = useTimeSeriesApi()
      const aggregationResult = await fetchTimeSeriesAggregation(queryParams)

      // Converts the aggregation result to the chart data.
      const chartData = CampaignHelper.toChartData(aggregationResult, this.selectedDataSeries, campaignDetails)

      // Updates the x axis range if the time range min and max are defined
      if (this.timeRange.min) {
        chartData.chartOptions.xaxis!.min = +this.timeRange.min
      }

      if (this.timeRange.max) {
        chartData.chartOptions.xaxis!.max = +this.timeRange.max
      }

      this.chartDataSeries = chartData.chartDataSeries
      this.chartOptions = chartData.chartOptions
      this.isLoadingChart = false
    },
  },
})
