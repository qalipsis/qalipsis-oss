export const useTimeSeriesApi = () => {
    const { get$ } = baseApi();

    const fetchTimeSeriesAggregation = (queryParam: TimeSeriesAggregationQueryParam): Promise<{ [key: string]: TimeSeriesAggregationResult[] }> => {
        return get$("/time-series/aggregate", queryParam);
    }

    return {
        fetchTimeSeriesAggregation
    }
}