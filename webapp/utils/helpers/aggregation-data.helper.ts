
export const AggregationDataHelper = {
  getTimeSeriesAggregationQueryParam(config: AggregationDataConfig): TimeSeriesAggregationQueryParam {
    const { series, campaigns, availableScenarios, selectedScenarios, from, until } = config

    const queryParam: TimeSeriesAggregationQueryParam = {
      series: [SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE, ...series].join(','),
      campaigns: campaigns.join(','),
      ...(from && { from }),
      ...(until && { until }),
    }

    // Add selected scenarios to the request when not all scenarios are selected.
    if (selectedScenarios.length < availableScenarios.length) {
      queryParam.scenarios = selectedScenarios.join(',')
    }

    return queryParam
  },
}
