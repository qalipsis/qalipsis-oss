export const TimeSeriesHelper = {
    /**
     * Enriches the data series value with formatted value and texts.
     *
     * @param dataSeries data series
     * @param value value of the data
     * @returns the value with formatted value and text
     */
    toComposedValue(dataSeries: DataSeries, value: number): AggregationComposedValue {
        const isDurationNanoField = dataSeries?.fieldName === SeriesDetailsConfig.DURATION_NANO_FIELD_NAME
        const isMinionsCountSeries = dataSeries?.reference === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
        const decimal = isMinionsCountSeries ? 0 : (isDurationNanoField ? 6 : 2)
        const formattedValue = isDurationNanoField ? (value / 1_000_000).toFixed(6) : (value).toFixed(decimal)

        return {
            value: value,
            formattedValue: formattedValue,
            formattedText: isDurationNanoField ? `${formattedValue} ms` : formattedValue,
        }
    },
}
