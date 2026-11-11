/**
 * Number of nanoseconds in each time unit, named as the backend does (java.util.concurrent.TimeUnit).
 */
const NANOS_PER_TIME_UNIT: Record<string, number> = {
    NANOSECONDS: 1,
    MICROSECONDS: 1_000,
    MILLISECONDS: 1_000_000,
    SECONDS: 1_000_000_000,
    MINUTES: 60_000_000_000,
    HOURS: 3_600_000_000_000,
    DAYS: 86_400_000_000_000,
}

/**
 * Units to display the durations with, from the largest.
 */
const DURATION_DISPLAY_UNITS = [
    {label: 'h', nanos: NANOS_PER_TIME_UNIT.HOURS!},
    {label: 'min', nanos: NANOS_PER_TIME_UNIT.MINUTES!},
    {label: 's', nanos: NANOS_PER_TIME_UNIT.SECONDS!},
    {label: 'ms', nanos: NANOS_PER_TIME_UNIT.MILLISECONDS!},
    {label: 'µs', nanos: NANOS_PER_TIME_UNIT.MICROSECONDS!},
    {label: 'ns', nanos: NANOS_PER_TIME_UNIT.NANOSECONDS!},
]

/**
 * Durations of the charts are plotted in milliseconds, the axis labels adapt the unit.
 */
const PLOT_DURATION_UNIT_NANOS = NANOS_PER_TIME_UNIT.MILLISECONDS!

/**
 * Aggregations that do not return a value of the field itself, so not a duration for a duration field.
 */
const NON_DURATION_AGGREGATIONS: ReadonlySet<string> = new Set([QueryAggregationOperatorConstant.COUNT])

export const DurationFormatHelper = {
    /**
     * Returns the number of nanoseconds in the time unit, or undefined when the unit is not a time unit.
     */
    nanosPerUnit(unit?: string | null): number | undefined {
        return unit ? NANOS_PER_TIME_UNIT[unit.toUpperCase()] : undefined
    },

    /**
     * Formats a duration in nanoseconds with the most readable unit, for example "350 µs", "12.5 ms" or "1.2 s".
     */
    format(nanos: number): string {
        if (!Number.isFinite(nanos) || nanos === 0) return '0'
        const absolute = Math.abs(nanos)
        const unit = DURATION_DISPLAY_UNITS.find((u) => absolute >= u.nanos) ?? DURATION_DISPLAY_UNITS.at(-1)!
        const value = nanos / unit.nanos
        const digits = Math.abs(value) < 10 ? 2 : Math.abs(value) < 100 ? 1 : 0

        return `${Number(value.toFixed(digits))} ${unit.label}`
    },

    /**
     * Formats a duration serialized as ISO-8601 string (e.g. "PT0.000224S") with the most readable unit.
     */
    formatIso(isoDuration: string): string {
        return DurationFormatHelper.format(
            TimeframeHelper.isoStringToTargetTimeframeUnit(isoDuration, 'MS') * NANOS_PER_TIME_UNIT.MILLISECONDS!,
        )
    },
}

export interface SeriesFormat {
    /**
     * The values of the series are durations.
     */
    isDuration: boolean
    isMinionsCountSeries: boolean
    decimal: number
    /**
     * Converts a value of the series into the value to plot (milliseconds for the durations).
     */
    toPlotValue: (value: number) => number | string
    /**
     * Formats a value of the series as text, with its unit for the durations.
     */
    format: (value: number) => string
    /**
     * Formats a plotted value for the axis labels.
     */
    formatPlotValue: (plotValue: number) => string
}

/**
 * Returns the number of nanoseconds in the unit of the values of the data series, if they are durations.
 */
function getDurationUnitNanos(dataSeries?: DataSeries): number | undefined {
    if (!dataSeries || NON_DURATION_AGGREGATIONS.has(dataSeries.aggregationOperation ?? '')) return undefined
    const unit = dataSeries.fieldUnit
        ?? (dataSeries.fieldName === SeriesDetailsConfig.DURATION_NANO_FIELD_NAME ? 'NANOSECONDS' : undefined)

    return DurationFormatHelper.nanosPerUnit(unit)
}

/**
 * Derives unit/decimal/formatting rules for a data series.
 *
 * @param dataSeries The resolved series definition, if available. Used to detect the duration fields.
 * @param reference Optional series reference. Preferred over `dataSeries.reference` so callers can detect
 *                  the built-in minions-count series even when no definition is registered for it.
 */
export function getSeriesFormat(dataSeries?: DataSeries, reference?: string): SeriesFormat {
    const key = reference ?? dataSeries?.reference
    const isMinionsCountSeries = key === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
    const durationUnitNanos = getDurationUnitNanos(dataSeries)
    const decimal = isMinionsCountSeries ? 0 : 2
    if (durationUnitNanos !== undefined) {
        return {
            isDuration: true,
            isMinionsCountSeries,
            decimal,
            toPlotValue: (value) => (value * durationUnitNanos) / PLOT_DURATION_UNIT_NANOS,
            format: (value) => DurationFormatHelper.format(value * durationUnitNanos),
            formatPlotValue: (plotValue) => DurationFormatHelper.format(plotValue * PLOT_DURATION_UNIT_NANOS),
        }
    }
    const format = (value: number): string => value.toFixed(decimal)

    return {
        isDuration: false,
        isMinionsCountSeries,
        decimal,
        toPlotValue: format,
        format,
        formatPlotValue: format,
    }
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
            formattedText: formattedValue,
        }
    },
}
