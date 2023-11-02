export const useTimeSeriesApi = () => {
    const { get$ } = baseApi();

    const fetchTimeSeriesAggregation = (queryParam: TimeSeriesAggregationQueryParam): Promise<{ [key: string]: TimeSeriesAggregationResult[] }> => {
        return get$<{ [key: string]: TimeSeriesAggregationResult[] }, any>("/time-series/aggregate", queryParam);
    }

    const fetchCampaignSummary = (queryParam: CampaignSummaryResultQueryParams): Promise<CampaignSummaryResult[]> => {
        console.log(queryParam)
        return get$<CampaignSummaryResult[], any>("/time-series/summary/campaign-status", queryParam);
    }

    return {
        fetchTimeSeriesAggregation,
        fetchCampaignSummary
    }
}