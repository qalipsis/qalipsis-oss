import tinycolor from 'tinycolor2'

export const ChartHelper = {
    toChartOptionData(key: string, dataSeries: DataSeries[]): ChartOptionData {
        const seriesDefinition = dataSeries.find(s => s.reference === key)
        const isDurationNanoField = seriesDefinition?.fieldName === SeriesDetailsConfig.DURATION_NANO_FIELD_NAME
        const isMinionsCountSeries = key === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE

        return {
            dataSeriesName: seriesDefinition?.displayName ?? key,
            dataSeriesColor: seriesDefinition?.color && tinycolor(seriesDefinition.color).isValid()
                ? seriesDefinition.color
                : ColorsConfig.PURPLE_COLOR_HEX_CODE,
            isDurationNanoField,
            isMinionsCountSeries,
            decimal: isMinionsCountSeries ? 0 : isDurationNanoField ? 6 : 2,
        }
    },

    formatYValue(chartOptionData: ChartOptionData, value: number): string {
        return chartOptionData.isDurationNanoField
            ? (value / 1_000_000).toFixed(6)
            : value.toFixed(chartOptionData.decimal)
    },

    getDataSeries(
        chartOptionData: ChartOptionData,
        timeAggregationResults: TimeSeriesAggregationResult[]
    ): ApexDataSeries {
        return {
            name: chartOptionData.dataSeriesName,
            data: timeAggregationResults.map((v) => ({
                x: new Date(v.start).getTime(),
                y: ChartHelper.formatYValue(chartOptionData, v.value),
            })),
            color: chartOptionData.dataSeriesColor,
        }
    },

    getYAxisOptions(chartOptionData: ChartOptionData): ApexYAxis {
        return {
            seriesName: chartOptionData.dataSeriesName,
            opposite: !chartOptionData.isMinionsCountSeries,
            decimalsInFloat: chartOptionData.decimal,
            labels: {
                show: true,
                formatter: (val, _) => {
                    return `${val.toFixed(chartOptionData.decimal)}${
                        chartOptionData.isDurationNanoField ? " ms" : ""
                    }`
                },
                style: {
                    colors: [chartOptionData.dataSeriesColor],
                    cssClass: "apexcharts-yaxis-label",
                },
            },
            title: {
                text: chartOptionData.isMinionsCountSeries ? "Minions count" : "",
                style: {
                    cssClass: "fill-gray-800 dark:fill-gray-50",
                },
            },
        }
    },

    getEmptyChartYAxisOptions(scheduledMinions?: number): ApexYAxis {
        const yAxisConfig: ApexYAxis = {
            min: 0,
            showForNullSeries: true,
            decimalsInFloat: 0,
            floating: false,
            axisBorder: {
                show: true,
                color: "#e0e0e0",
            },
            labels: {
                show: true,
                style: {
                    cssClass: "fill-gray-800 dark:fill-gray-50",
                },
                formatter: (val: number, _: any) => val.toFixed(0),
            },
            title: {
                text: "Minions Count",
                style: {
                    cssClass: "fill-gray-800 dark:fill-gray-50",
                },
            },
        }

        if (scheduledMinions) {
            yAxisConfig.max = scheduledMinions
            if (scheduledMinions <= 10) {
                yAxisConfig.tickAmount = scheduledMinions
            }
        }

        return yAxisConfig
    },
}
