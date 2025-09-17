import {SharingModeConstant} from '../types/series'

export class SeriesHelper {
    static sharingModeToText = {
        [SharingModeConstant.READONLY]: 'Read Only',
        [SharingModeConstant.NONE]: 'None',
        [SharingModeConstant.WRITE]: 'Write',
    }

    static MINIONS_COUNT_DATA_SERIES_REFERENCE = 'minions.count'

    static toDataSeriesCreationRequest(form: DataSeriesForm): DataSeriesCreationRequest {
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
            aggregationOperation: form.aggregationOperation!,
        }
    }

    static toDataSeriesPatchRequest(
        originalDataSeriesForm: DataSeriesForm,
        newDataSeriesForm: DataSeriesForm
    ): DataSeriesPatch[] {
        const seriesPatchRequests: DataSeriesPatch[] = []
        Object.keys(newDataSeriesForm).forEach((key) => {
            const formKey = key as keyof typeof originalDataSeriesForm
            const isChanged =
                key === 'filters'
                    ? !this.areFiltersIdentical(originalDataSeriesForm.filters, newDataSeriesForm.filters)
                    : originalDataSeriesForm[formKey] !== newDataSeriesForm[formKey]
            if (isChanged) {
                const patchRequest = this.getPatchRequest(key, newDataSeriesForm)
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
    }

    static toDataSeriesOptions(dataSeries: DataSeries[], selectedDataSeriesReferences: string[]): DataSeriesOption[] {
        return dataSeries.map((el) => ({
            ...el,
            display: el.color ?? ColorsConfig.PRIMARY_COLOR_HEX_CODE,
            isActive: selectedDataSeriesReferences.includes(el.reference),
        }))
    }

    static toDataSeriesTableData(dataSeries: DataSeries[], userName: string): DataSeriesTableData[] {
        return dataSeries.map<DataSeriesTableData>((el) => ({
            ...el,
            key: el.reference,
            sharedText: SeriesHelper.sharingModeToText[el.sharingMode!],
            filterNames: el.filters?.map((filter) => filter.name),
            formattedTimeframe: TimeframeHelper.toFormattedTimeframe(el.timeframeUnit),
            disabled: el.creator !== userName && el.sharingMode === SharingModeConstant.READONLY,
        }))
    }

    static getFilterOperatorOptions(): FormMenuOption[] {
        return [
            {
                label: 'Is',
                value: QueryClauseOperatorConstant.IS,
            },
            {
                label: 'Not equal to',
                value: QueryClauseOperatorConstant.IS_NOT,
            },
            {
                label: 'In',
                value: QueryClauseOperatorConstant.IS_IN,
            },
            {
                label: 'Not in',
                value: QueryClauseOperatorConstant.IS_NOT_IN,
            },
            {
                label: 'Like',
                value: QueryClauseOperatorConstant.IS_LIKE,
            },
            {
                label: 'Not Like',
                value: QueryClauseOperatorConstant.IS_NOT_LIKE,
            },
            {
                label: 'Greater than',
                value: QueryClauseOperatorConstant.IS_GREATER_THAN,
            },
            {
                label: 'Greater than or equal to',
                value: QueryClauseOperatorConstant.IS_GREATER_OR_EQUAL_TO,
            },
            {
                label: 'Less than',
                value: QueryClauseOperatorConstant.IS_LOWER_THAN,
            },
            {
                label: 'Less than or equal to',
                value: QueryClauseOperatorConstant.IS_LOWER_OR_EQUAL_TO,
            },
        ]
    }

    static getAggregationOperatorOptions(): FormMenuOption[] {
        return [
            {
                label: 'Average',
                value: QueryAggregationOperatorConstant.AVERAGE,
            },
            {
                label: 'Max',
                value: QueryAggregationOperatorConstant.MAX,
            },
            {
                label: 'Min',
                value: QueryAggregationOperatorConstant.MIN,
            },
            {
                label: 'Sum',
                value: QueryAggregationOperatorConstant.SUM,
            },
            {
                label: 'Standard deviation',
                value: QueryAggregationOperatorConstant.STANDARD_DEVIATION,
            },
            {
                label: 'Count',
                value: QueryAggregationOperatorConstant.COUNT,
            },
            {
                label: 'Percentile 75%',
                value: QueryAggregationOperatorConstant.PERCENTILE_75,
            },
            {
                label: 'Percentile 99%',
                value: QueryAggregationOperatorConstant.PERCENTILE_99,
            },
            {
                label: 'Percentile 99.9%',
                value: QueryAggregationOperatorConstant.PERCENTILE_99_9,
            },
        ]
    }

    static getTimeSeriesTypeOptions(): FormMenuOption[] {
        return [
            {
                value: DataTypeConstant.EVENTS,
                label: 'Event',
            },
            {
                value: DataTypeConstant.METERS,
                label: 'Meter',
            },
        ]
    }

    static getSharingModeOptions(): FormMenuOption[] {
        return [
            {
                label: 'None',
                value: SharingModeConstant.NONE,
            },
            {
                label: 'Read only',
                value: SharingModeConstant.READONLY,
            },
            {
                label: 'Write',
                value: SharingModeConstant.WRITE,
            },
        ]
    }

    /**
     * Checks if two filter list are the same
     *
     * @param originalDataSeriesFilters The first filter list.
     * @param newDataSeriesFilters The second filter list.
     * @returns A flag to indicate if two list are the same.
     */
    private static areFiltersIdentical = (
        originalDataSeriesFilters: DataSeriesFilter[],
        newDataSeriesFilters: DataSeriesFilter[]
    ): boolean => {
        return arraysEqual<DataSeriesFilter>(originalDataSeriesFilters, newDataSeriesFilters)
    }

    /**
     * Gets the patch request of the field from the series form
     *
     * @param key The field key of the form.
     * @param formValue The form values.
     * @returns The data series patch request.
     */
    private static getPatchRequest = (key: string, formValue: DataSeriesForm): DataSeriesPatch => {
        switch (key) {
            case 'name':
                return new DisplayNameDataSeriesPatch(formValue.name)
            case 'sharingMode':
                return new SharingModeDataSeriesPatch(formValue.sharingMode!)
            case 'fieldName':
                return new FieldNameDataSeriesPatch(formValue.fieldName)
            case 'valueName':
                return new ValueNameDataSeriesPatch(formValue.valueName)
            case 'aggregationOperation':
                return new AggregationDataSeriesPatch(formValue.aggregationOperation!)
            case 'timeframeValue':
            case 'timeframeUnit':
                return new TimeframeDataSeriesPatch(
                    TimeframeHelper.toIsoStringDuration(formValue.timeframeValue!, formValue.timeframeUnit)
                )
            case 'color':
            case 'colorOpacity':
                return new ColorDataSeriesPatch(formValue.color, formValue.colorOpacity)
            case 'filters':
                return new FilterDataSeriesPatch(formValue.filters)
            default:
                throw new Error(`Unknown form key ${key}`)
        }
    }
}
