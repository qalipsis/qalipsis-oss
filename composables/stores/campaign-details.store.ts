import type { ApexOptions } from "apexcharts";

interface CampaignDetailsStoreState {
  campaignDetails: CampaignExecutionDetails | null,
  selectedScenarioNames: string[],
  selectedDataSeries: DataSeries[]
  timeRange: {
    max: string,
    min: string
  },
  chartOptions: ApexOptions | null,
  chartDataSeries: ApexAxisChartSeries,
  isLoadingChart: boolean
}

export const useCampaignDetailsStore = defineStore("CampaignDetails", {
  state: (): CampaignDetailsStoreState => {
    return {
      campaignDetails: null,
      selectedScenarioNames: [],
      selectedDataSeries: [],
      timeRange: {
        min: '',
        max: ''
      },
      chartOptions: null,
      chartDataSeries: [],
      isLoadingChart: false
    }
  },
  getters: {
    selectedScenarioReports: state => ScenarioHelper.getSelectedScenarioReports(state.selectedScenarioNames, state.campaignDetails!),
    selectedDataSeriesReferences: state => state.selectedDataSeries.map(dataSeries => dataSeries.reference)
  },
  actions: {
    async updateChart(): Promise<void> {
      if (!this.campaignDetails) return;

      // Prepares the query params.
      const queryParams: TimeSeriesAggregationQueryParam = {
        campaigns: this.campaignDetails.key,
        // Note: always add the minions count reference in the query params.
        series: [SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE, ...this.selectedDataSeriesReferences].join(','),
        scenarios: this.selectedScenarioNames.join(','),
        from: this.campaignDetails.start,
      }
      if (this.campaignDetails.end) {
        queryParams.until = this.campaignDetails.end;
      }
      // Fetches the data from the time series service.
      this.isLoadingChart = true;
      const { fetchTimeSeriesAggregation } = useTimeSeriesApi();
      const aggregationResult = await fetchTimeSeriesAggregation(queryParams);
      // Converts the aggregation result to the chart data.
      const chartData = CampaignHelper.toChartData(aggregationResult, this.selectedDataSeries, this.campaignDetails);
      
      // Updates the x axis range if the time range min and max are defined
      if (this.timeRange.min) {
        chartData.chartOptions.xaxis!.min = +this.timeRange.min;
      }
      
      if (this.timeRange.max) {
        chartData.chartOptions.xaxis!.max = +this.timeRange.max;
      }
      
      this.chartDataSeries = chartData.chartDataSeries;
      this.chartOptions = chartData.chartOptions;
      this.isLoadingChart = false;
    }
  }
});
