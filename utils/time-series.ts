export interface TimeSeriesAggregationQueryParam {
    /**
     * References of the data-series describing the data to aggregate
     */
    series: string;

    /**
     * References of the campaigns to aggregate data on
     */
    campaigns: string;
    
    /**
     * Names of the scenarios to aggregate data on, defaults to all the scenarios of the specified campaigns
     */
    scenarios?: string;

    /**
     * Beginning of the aggregation window
     */
    from?: string;

    /**
     * End of the aggregation window
     */
    until?: string;

    /**
     * Size of the time-buckets to perform the aggregations
     */
    timeframe?: string;
}

/**
 * Single point of result of an aggregation of time-series data generated during campaign executions
 */
export interface TimeSeriesAggregationResult {
    /**
     * Start of the aggregation bucket
     */
    start: string;

    /**
     * Elapsed time between the start of the aggregation and the start of this result
     */
    elapsed: number;

    /**
     * Key of the campaign that generated the data
     */
    campaign: string;

    /**
     * Numeric result of the aggregation
     */
    value: number;
}

export interface ComposedAggregationValue {
    value: number;
    formattedValue: string;
    formattedText: string;
}