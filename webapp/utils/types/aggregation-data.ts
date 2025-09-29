export interface AggregationDataConfig {
  /**
   * The list of campaign keys.
   */
  campaigns: string[]
  /**
   * The list of selected series.
   */
  series: string[]

  /**
   * The list of selected scenario names.
   */
  selectedScenarios: string[]

  /**
   * Available scenario names.
   */
  availableScenarios: string[]

  /**
   * Beginning of the aggregation window.
   */
  from?: string

  /**
   * End of the aggregation window.
   */
  until?: string

  /**
   * Size of the time-buckets to perform the aggregations.
   */
  timeframe?: string
}
