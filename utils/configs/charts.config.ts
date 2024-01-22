import { ApexOptions } from "apexcharts";
import { format } from "date-fns";

export class ChartsConfig {
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
}