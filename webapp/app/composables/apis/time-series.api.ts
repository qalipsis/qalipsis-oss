export const useTimeSeriesApi = () => {
  const { get$ } = baseApi()

  /**
   * Fetches aggregated time series data for the given query parameters.
   *
   * @param queryParam The aggregation query parameters including series references, campaigns, time range, and timeframe.
   * @returns A map of series reference to their aggregated result arrays.
   */
  const fetchTimeSeriesAggregation = (
    queryParam: TimeSeriesAggregationQueryParam,
  ): Promise<{ [key: string]: TimeSeriesValues }> => {
      return get$<{ [key: string]: TimeSeriesValues }, TimeSeriesAggregationQueryParam>(
      '/time-series/aggregate',
      queryParam,
    )
  }

  /**
   * Fetches campaign status summary statistics for the given time range.
   *
   * @param queryParam The query parameters including time range and optional timeframe offset.
   * @returns The list of campaign summary results.
   */
  const fetchCampaignSummary = (queryParam: CampaignSummaryResultQueryParams): Promise<CampaignSummaryResult[]> => {
    return get$<CampaignSummaryResult[], CampaignSummaryResultQueryParams>(
      '/time-series/summary/campaign-status',
      queryParam,
    )
  }

  return {
    fetchTimeSeriesAggregation,
    fetchCampaignSummary,
  }
}
