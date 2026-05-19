export interface ReportTableData extends DataReport {
    /**
     * The concatenation of the campaign names.
     */
    concatenatedCampaignNames?: string;
}

export interface ReportDetailsTableData {
    /**
     * The unique identifier for the report details table data.
     */
    id: string;
    /**
     * The reference of the data series
     */
    seriesReference: string;
    /**
     * The name of the data series
     */
    seriesName: string;
    /**
     * The name of the campaign
     */
    campaignName: string;
    /**
     * The key of the campaign
     */
    campaignKey: string;
    /**
     * The start time from the aggregation result
     */
    startTime: string;
    /**
     * The elapsed time from the aggregation result
     */
    elapsed: number;
    /**
     * The text to display the elapsed time
     */
    elapsedText: string;
    /**
     * The value from the aggregation result.
     */
    value: number;
    /**
     * The text to display the start time
     */
    startTimeText: string;
    /**
     * The text to display the value
     */
    valueDisplayText: string;
}
