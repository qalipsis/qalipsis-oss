/**
 * Checks if two filter lists are the same.
 */
function areFiltersIdentical(
    originalDataSeriesFilters: DataSeriesFilter[],
    newDataSeriesFilters: DataSeriesFilter[]
): boolean {
    return arraysEqual<DataSeriesFilter>(originalDataSeriesFilters, newDataSeriesFilters)
}

const buildTimeframePatch = (form: DataSeriesForm): DataSeriesPatch =>
    new TimeframeDataSeriesPatch(TimeframeHelper.toIsoStringDuration(form.timeframeValue!, form.timeframeUnit))

const buildColorPatch = (form: DataSeriesForm): DataSeriesPatch =>
    new ColorDataSeriesPatch(form.color, form.colorOpacity)

const patchBuilders: Record<string, (form: DataSeriesForm) => DataSeriesPatch> = {
    name: (f) => new DisplayNameDataSeriesPatch(f.name),
    sharingMode: (f) => new SharingModeDataSeriesPatch(f.sharingMode!),
    fieldName: (f) => new FieldNameDataSeriesPatch(f.fieldName),
    valueName: (f) => new ValueNameDataSeriesPatch(f.valueName),
    aggregationOperation: (f) => new AggregationDataSeriesPatch(f.dataType === DataTypeConstant.METERS ? null : f.aggregationOperation!),
    timeframeValue: buildTimeframePatch,
    timeframeUnit: buildTimeframePatch,
    color: buildColorPatch,
    colorOpacity: buildColorPatch,
    filters: (f) => new FilterDataSeriesPatch(f.filters),
}

/**
 * Gets the patch request of the field from the series form.
 */
function getPatchRequest(key: string, formValue: DataSeriesForm): DataSeriesPatch {
    const builder = patchBuilders[key]
    if (!builder) throw new Error(`Unknown form key ${key}`)

    return builder(formValue)
}

export const SeriesHelper = {
    sharingModeToText: {
        [SharingModeConstant.READONLY]: 'Read Only',
        [SharingModeConstant.NONE]: 'None',
        [SharingModeConstant.WRITE]: 'Write',
    },

    toDataSeriesCreationRequest(form: DataSeriesForm): DataSeriesCreationRequest {
        return {
            displayName: form.name,
            dataType: form.dataType,
            sharingMode: form.sharingMode!,
            valueName: form.valueName,
            fieldName: form.fieldName,
            filters: form.filters,
            color: form.color ?? null,
            colorOpacity: form.colorOpacity,
            timeframeUnit: TimeframeHelper.toIsoStringDuration(form.timeframeValue!, form.timeframeUnit),
            aggregationOperation: form.dataType === DataTypeConstant.METERS ? null : form.aggregationOperation!,
        }
    },

    toDataSeriesPatchRequest(
        originalDataSeriesForm: DataSeriesForm,
        newDataSeriesForm: DataSeriesForm
    ): DataSeriesPatch[] {
        const seriesPatchRequests: DataSeriesPatch[] = []
        Object.keys(newDataSeriesForm).forEach((key) => {
            const formKey = key as keyof typeof originalDataSeriesForm
            const isChanged =
                key === 'filters'
                    ? !areFiltersIdentical(originalDataSeriesForm.filters, newDataSeriesForm.filters)
                    : originalDataSeriesForm[formKey] !== newDataSeriesForm[formKey]
            if (isChanged) {
                const patchRequest = getPatchRequest(key, newDataSeriesForm)
                /**
                 * Only adds the patch request to the list when it is not yet added.
                 *
                 * @remarks
                 * There might be the same patch request.
                 * E.g.,
                 * 1. The changes for the timeframe value and timeframe unit are used for the timeframe patch request.
                 * 2. The changes for the color hex and color opacity are used for the color patch request.
                 */
                if (!seriesPatchRequests.some((s) => s.type === patchRequest.type)) {
                    seriesPatchRequests.push(patchRequest)
                }
            }
        })

        return seriesPatchRequests
    },

    toDataSeriesOptions(dataSeries: DataSeries[], selectedDataSeriesReferences: string[]): DataSeriesOption[] {
        return dataSeries.map((el) => ({
            ...el,
            display: el.color ?? ColorsConfig.PRIMARY_COLOR_HEX_CODE,
            isActive: selectedDataSeriesReferences.includes(el.reference),
        }))
    },

    toDataSeriesTableData(dataSeries: DataSeries[], userName: string): DataSeriesTableData[] {
        return dataSeries.map<DataSeriesTableData>((el) => {
            return {
                ...el,
                key: el.reference,
                sharedText: SeriesHelper.sharingModeToText[el.sharingMode!],
                filterNames: el.filters?.map((filter) => filter.name),
                formattedTimeframe: TimeframeHelper.toFormattedTimeframe(el.timeframeUnit),
                disabled: el.creator !== userName && el.sharingMode === SharingModeConstant.READONLY,
            }
        })
    },

    getFilterOperatorOptions(): FormMenuOption[] {
        return [
            { label: 'Is', value: QueryClauseOperatorConstant.IS },
            { label: 'Not equal to', value: QueryClauseOperatorConstant.IS_NOT },
            { label: 'In', value: QueryClauseOperatorConstant.IS_IN },
            { label: 'Not in', value: QueryClauseOperatorConstant.IS_NOT_IN },
            { label: 'Like', value: QueryClauseOperatorConstant.IS_LIKE },
            { label: 'Not Like', value: QueryClauseOperatorConstant.IS_NOT_LIKE },
            { label: 'Greater than', value: QueryClauseOperatorConstant.IS_GREATER_THAN },
            { label: 'Greater than or equal to', value: QueryClauseOperatorConstant.IS_GREATER_OR_EQUAL_TO },
            { label: 'Less than', value: QueryClauseOperatorConstant.IS_LOWER_THAN },
            { label: 'Less than or equal to', value: QueryClauseOperatorConstant.IS_LOWER_OR_EQUAL_TO },
        ]
    },

    getAggregationOperatorOptions(): FormMenuOption[] {
        return [
            { label: 'Average', value: QueryAggregationOperatorConstant.AVERAGE },
            { label: 'Max', value: QueryAggregationOperatorConstant.MAX },
            { label: 'Min', value: QueryAggregationOperatorConstant.MIN },
            { label: 'Sum', value: QueryAggregationOperatorConstant.SUM },
            { label: 'Standard deviation', value: QueryAggregationOperatorConstant.STANDARD_DEVIATION },
            { label: 'Count', value: QueryAggregationOperatorConstant.COUNT },
            { label: 'Percentile 75%', value: QueryAggregationOperatorConstant.PERCENTILE_75 },
            { label: 'Percentile 99%', value: QueryAggregationOperatorConstant.PERCENTILE_99 },
            { label: 'Percentile 99.9%', value: QueryAggregationOperatorConstant.PERCENTILE_99_9 },
        ]
    },

    getTimeSeriesTypeOptions(): FormMenuOption[] {
        return [
            { value: DataTypeConstant.EVENTS, label: 'Event' },
            { value: DataTypeConstant.METERS, label: 'Meter' },
        ]
    },

    getSharingModeOptions(): FormMenuOption[] {
        return [
            { label: 'None', value: SharingModeConstant.NONE },
            { label: 'Read only', value: SharingModeConstant.READONLY },
            { label: 'Write', value: SharingModeConstant.WRITE },
        ]
    },
}
