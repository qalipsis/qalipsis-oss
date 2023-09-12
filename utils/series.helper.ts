import { ColorHelper } from "./color.helper";
import { FormMenuOption } from "./form";
import { DataSeries, DataSeriesCreationRequest, DataSeriesForm, DataSeriesPatch } from "./series";
import { arraysEqual } from "./utils.helper";

export class SeriesHelper {
    static sharingModeToText = {
        [SharingMode.READONLY]: 'Read Only',
        [SharingMode.NONE]: 'None',
        [SharingMode.WRITE]: 'Write',
    }

    static MAX_DATA_SERIES_TO_BE_DISPLAYED = 10;

    static DURATION_NANO_FIELD_NAME = 'duration_nano';

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
            timeframeUnit: TimeframeHelper.toMilliseconds(form.timeframeValue!, form.timeframeUnit),
            aggregationOperation: form.aggregationOperation!
        };
    }

    static toDataSeriesPatchRequest(originalDataSeriesForm: DataSeriesForm, newDataSeriesForm: DataSeriesForm): DataSeriesPatch[] {
        const seriesPatchRequests: DataSeriesPatch[] = [];
        Object.keys(newDataSeriesForm).forEach(key => {
            const formKey = key as keyof typeof originalDataSeriesForm
            const isChanged = key === "filters" ?
                !this.areFiltersIdentical(originalDataSeriesForm.filters, newDataSeriesForm.filters) :
                originalDataSeriesForm[formKey] !== newDataSeriesForm[formKey]
            if (isChanged) {
                const patchRequest = this.getPatchRequest(key, newDataSeriesForm);
                /**
                 * Only adds the patch request to the list when it is not yet added. 
                 * 
                 * @remarks
                 * There might be the same patch request.
                 * E.g.,
                 * 1. The changes for the timeframe value and timeframe unit are used for the timeframe patch request.
                 * 2. The changes for the color hex and color opacity are used for the color patch request. 
                 */
                if (!seriesPatchRequests.some(s => s.type === patchRequest.type)) {
                    seriesPatchRequests.push(patchRequest);
                }
            }
        });

        return seriesPatchRequests;
    }

    static toDataSeriesOptions(dataSeries: DataSeries[], selectedDataSeriesReferences: string[]): DataSeriesOption[] {
        return dataSeries.map(el => ({
            ...el,
            display: el.color ?? ColorHelper.PRIMARY_COLOR_HEX_CODE,
            isActive: selectedDataSeriesReferences.includes(el.reference)
        }))
    }

    static toDataSeriesTableData(dataSeries: DataSeries[], userName: string): DataSeriesTableData[] {
        return dataSeries.map(el => ({
            ...el,
            key: el.reference,
            sharedText: SeriesHelper.sharingModeToText[el.sharingMode],
            filterNames: el.filters?.map(filter => filter.name),
            formattedTimeframe: TimeframeHelper.toFormattedTimeframe(el.timeframeUnit),
            disabled: (el.creator !== userName && el.sharingMode === SharingMode.READONLY) || el.reference === SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE
        }))
    }

    static getFilterOperatorOptions(): FormMenuOption[] {
        return [
            {
                label: 'Is',
                value: QueryClauseOperator.IS,
            },
            {
                label: 'Not equal to',
                value: QueryClauseOperator.IS_NOT,
            },
            {
                label: 'In',
                value: QueryClauseOperator.IS_IN,
            },
            {
                label: 'Not in',
                value: QueryClauseOperator.IS_NOT_IN,
            },
            {
                label: 'Like',
                value: QueryClauseOperator.IS_LIKE,
            },
            {
                label: 'Not Like',
                value: QueryClauseOperator.IS_NOT_LIKE,
            },
            {
                label: 'Greater than',
                value: QueryClauseOperator.IS_GREATER_THAN,
            },
            {
                label: 'Greater than or equal to',
                value: QueryClauseOperator.IS_GREATER_OR_EQUAL_TO,
            },
            {
                label: 'Less than',
                value: QueryClauseOperator.IS_LOWER_THAN,
            },
            {
                label: 'Less than or equal to',
                value: QueryClauseOperator.IS_LOWER_OR_EQUAL_TO,
            },
        ]
    }

    static getAggregationOperatorOptions(): FormMenuOption[] {
        return [
            {
                label: 'Average',
                value: QueryAggregationOperator.AVERAGE,
            },
            {
                label: 'Max',
                value: QueryAggregationOperator.MAX,
            },
            {
                label: 'Min',
                value: QueryAggregationOperator.MIN,
            },
            {
                label: 'Sum',
                value: QueryAggregationOperator.SUM,
            },
            {
                label: 'Standard deviation',
                value: QueryAggregationOperator.STANDARD_DEVIATION
            },
            {
                label: 'Count',
                value: QueryAggregationOperator.COUNT,
            },
            {
                label: 'Percentile 75%',
                value: QueryAggregationOperator.PERCENTILE_75
            },
            {
                label: 'Percentile 99%',
                value: QueryAggregationOperator.PERCENTILE_99
            },
            {
                label: 'Percentile 99.9%',
                value: QueryAggregationOperator.PERCENTILE_99_9
            },
        ]
    }

    static getTimeSeriesTypeOptions(): FormMenuOption[] {
        return [
            {
                value: DataType.EVENTS,
                label: 'Event'
            },
            {
                value: DataType.METERS,
                label: 'Meter'
            }
        ]
    }

    static getSharingModeOptions(): FormMenuOption[] {
        return [
            {
                label: 'None',
                value: SharingMode.NONE,
            },
            {
                label: 'Read only',
                value: SharingMode.READONLY,
            },
            {
                label: 'Write',
                value: SharingMode.WRITE,
            }
        ];
    }

    static getTableColumnConfigs() {
        return [
            {
                title: 'Name',
                dataIndex: 'displayName',
                key: 'displayName',
                sorter: (next: DataSeriesTableData, prev: DataSeriesTableData) => next.displayName.localeCompare(prev.displayName),
            },
            {
                title: 'Time-series type',
                dataIndex: 'dataType',
                key: 'dataType',
                sorter: (next: DataSeriesTableData, prev: DataSeriesTableData) => next.dataType.localeCompare(prev.dataType),
            },
            {
                title: 'Field name',
                dataIndex: 'fieldName',
                key: 'fieldName',
            },
            {
                title: 'Value',
                dataIndex: 'valueName',
                key: 'valueName',
            },
            {
                title: 'Shared',
                dataIndex: 'sharingMode',
                key: 'sharingMode',
                sorter: (next: DataSeriesTableData, prev: DataSeriesTableData) => next.sharingMode.localeCompare(prev.sharingMode),
            },
            {
                title: '',
                dataIndex: 'actions',
                key: 'actions',
            }
        ];
    }

    /**
     * Checks if two filter list are the same
     * 
     * @param originalDataSeriesFilters The first filter list.
     * @param newDataSeriesFilters The second filter list.
     * @returns A flag to indicate if two list are the same.
     */
    private static areFiltersIdentical = (originalDataSeriesFilters: DataSeriesFilter[], newDataSeriesFilters: DataSeriesFilter[]): boolean => {
        return arraysEqual<DataSeriesFilter>(originalDataSeriesFilters, newDataSeriesFilters);
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
                return {
                    displayName: formValue.name,
                    type: DataSeriesPatchTypeEnum.DISPLAY_NAME
                }
            case 'sharingMode':
                return {
                    sharingMode: formValue.sharingMode!,
                    type: DataSeriesPatchTypeEnum.SHARING_MODE
                }
            case 'fieldName':
                return {
                    fieldName: formValue.fieldName,
                    type: DataSeriesPatchTypeEnum.FIELD_NAME
                }
            case 'valueName':
                return {
                    valueName: formValue.valueName,
                    type: DataSeriesPatchTypeEnum.VALUE_NAME,
                }
            case 'aggregationOperation':
                return {
                    operation: formValue.aggregationOperation!,
                    type: DataSeriesPatchTypeEnum.AGGREGATION,
                }
            case 'timeframeValue':
            case 'timeframeUnit':
                return {
                    timeframe: TimeframeHelper.toMilliseconds(formValue.timeframeValue!, formValue.timeframeUnit),
                    type: DataSeriesPatchTypeEnum.TIME_FRAME,
                }
            case 'color':
            case 'colorOpacity':
                return {
                    type: DataSeriesPatchTypeEnum.COLOR,
                    color: formValue.color,
                    opacity: formValue.colorOpacity
                }
            case 'filters':
                return {
                    type: DataSeriesPatchTypeEnum.FILTERS,
                    filters: formValue.filters
                }
            default:
                throw new Error(`Unknown form key ${key}`);
        }
    }
}