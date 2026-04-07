/**
 * An interface for the data series option
 */
export interface DataSeriesOption extends DataSeries {
    /**
     * A flag to indicate if the options is selected
     */
    isActive: boolean
}

/**
 * An interface that describes the data displayed on the series table.
 */
export interface DataSeriesTableData extends DataSeries {
    /**
     * The key for the row selection.
     *
     * @remarks
     * "key" is the default property name for the row selection
     * from the ant design table component.
     */
    key: string

    /**
     * The display text for the sharing mode.
     */
    sharedText: string

    /**
     * The formatted timeframe from the timeframeUnit value.
     */
    formattedTimeframe: FormattedTimeframe

    /**
     * The list of the field name to apply the filter.
     */
    filterNames?: string[]

    /**
     * A flag to check if the data series is disabled for editing.
     */
    disabled: boolean
}


export interface DataSeriesForm {
    name: string
    sharingMode: SharingMode | null
    dataType: DataType
    valueName: string
    fieldName: string
    aggregationOperation: QueryAggregationOperator | null
    timeframeValue: number | null
    timeframeUnit: TimeframeUnit
    color: string
    colorOpacity: number
    filters: DataSeriesFilter[]
}
