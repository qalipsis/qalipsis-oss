import { ApexOptions } from "apexcharts";
import { format } from "date-fns";

export class ChartHelper {
    static DEFAULT_CHART_OPTIONS: ApexOptions = {
        noData: {
            text: 'No data yet, the screen will be refreshed soon...'
        },
        stroke: {
            curve: 'straight',
            width: 1
        },
        chart: {
            type: 'line',
            toolbar: {
                show: true,
                offsetY: -28,
                tools: {
                    download: false,
                    selection: false,
                    zoom: true,
                    zoomin: true,
                    zoomout: true,
                    pan: false,
                    reset: true,
                    customIcons: []
                },
                autoSelected: 'zoom',
            },
            zoom: {
                enabled: true,
            },
            selection: {
                enabled: true,
                type: 'x',
                fill: {
                    color: '#abb1c0',
                    opacity: 0.4
                },
                stroke: {
                    width: 0.8,
                },
            },
            fontFamily: 'Outfit, sans-serif',
            redrawOnParentResize: true,
            redrawOnWindowResize: true
        },
        xaxis: {
            title: {
                text: 'Time',
                offsetY: 75,
                style: {
                    cssClass: 'apexcharts-yaxis-title',
                },
            },
            type: "datetime",
            tooltip: {
                formatter: (value: string) => format(new Date(value), 'HH:mm:ss'),
            },
            labels: {
                format: "HH:mm:ss",
                style: {
                    colors: '#000000',
                },
                datetimeUTC: false,
            },
        },
        yaxis: [],
        dataLabels: {
            enabled: false
        },
        tooltip: {
            enabled: true,
            shared: true,
            followCursor: false,
            inverseOrder: true,
            x: {
                show: false,
                format: 'HH:mm:ss',
            },
            marker: {
                show: true,
            }
        },
        markers: {
            size: 0,
        },
        legend: {
            show: false,
            showForSingleSeries: true,
            showForNullSeries: false,
        },
        series: []
    }

    static getDataSeries(chartOptionData: ChartOptionData, timeAggregationResults: TimeSeriesAggregationResult[]): ApexDataSeries {
        return {
            name: chartOptionData.dataSeriesName,
            data: timeAggregationResults.map(v => ({
                x: new Date(v.start).getTime(),
                y: chartOptionData.isDurationNanoField ? (v.value / 1_000_000).toFixed(6) : (v.value).toFixed(chartOptionData.decimal)
            })),
            color: chartOptionData.dataSeriesColor
        }
    }

    static getYAxisOptions(chartOptionData: ChartOptionData): ApexYAxis {
        return {
            seriesName: chartOptionData.dataSeriesName,
            opposite: !chartOptionData.isMinionsCountSeries, // Position the minions count AXIS on the left side.
            decimalsInFloat: chartOptionData.decimal,
            labels: {
                show: true,
                formatter: (val, _) => {
                    // Format the y axis label
                    return `${val.toFixed(chartOptionData.decimal)}${chartOptionData.isDurationNanoField ? ' ms' : ''}`
                },
                style: {
                    colors: [chartOptionData.dataSeriesColor],
                    cssClass: 'apexcharts-yaxis-label'
                }
            },
            title: {
                text: chartOptionData.isMinionsCountSeries ? 'Minions count' : '',
            }
        }
    }

    static getEmptyChartYAxisOptions(scheduledMinions?: number): ApexYAxis {
        const yAxisConfig: ApexYAxis = {
            min: 0,
            showForNullSeries: true,
            decimalsInFloat: 0,
            floating: false,
            axisBorder: {
                show: true,
                color: '#e0e0e0'
            },
            labels: {
                show: true,
                style: {
                    colors: '#000',
                },
                formatter: (val: number, _: any) => val.toFixed(2)
            },
            title: {
                text: 'Minions Count',
                style: {
                    cssClass: 'apexcharts-yaxis-title',
                },
            }
        }

        if (scheduledMinions) {
            yAxisConfig.max = scheduledMinions;
            if (scheduledMinions <= 10) {
                yAxisConfig.tickAmount = scheduledMinions;
            }
        }

        return yAxisConfig
    }
}