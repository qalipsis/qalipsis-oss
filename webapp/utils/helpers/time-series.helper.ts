export interface SeriesFormat {
    isDurationNanoField: boolean
    isMinionsCountSeries: boolean
    decimal: number
    format: (value: number) => string
}

/**
 * Derives unit/decimal/formatting rules for a data series.
 *
 * @param dataSeries The resolved series definition, if available. Used to detect duration-nano fields.
 * @param reference Optional series reference. Preferred over `dataSeries.reference` so callers can detect
 *                  the built-in minions-count series even when no definition is registered for it.
 */
export function getSeriesFormat(dataSeries?: DataSeries, reference?: string): SeriesFormat {
    const key = reference ?? dataSeries?.reference
    const isDurationNanoField = dataSeries?.fieldName === SeriesDetailsConfig.DURATION_NANO_FIELD_NAME
    const isMinionsCountSeries = key === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
    const decimal = isMinionsCountSeries ? 0 : isDurationNanoField ? 6 : 2
    const format = (value: number): string =>
        isDurationNanoField ? (value / 1_000_000).toFixed(6) : value.toFixed(decimal)

    return { isDurationNanoField, isMinionsCountSeries, decimal, format }
}

export const TimeSeriesHelper = {
    /**
     * Enriches the data series value with formatted value and texts.
     *
     * @param dataSeries data series
     * @param value value of the data
     * @returns the value with formatted value and text
     */
    toComposedValue(dataSeries: DataSeries, value: number): AggregationComposedValue {
        const fmt = getSeriesFormat(dataSeries)
        const formattedValue = fmt.format(value)

        return {
            value,
            formattedValue,
            formattedText: fmt.isDurationNanoField ? `${formattedValue} ms` : formattedValue,
        }
    },
}
