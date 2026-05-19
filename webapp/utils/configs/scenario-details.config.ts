import type { ApexOptions } from 'apexcharts'

export const ScenarioDetailsConfig = {
  /**
   * The name for the scenario summary.
   */
  SCENARIO_SUMMARY_NAME: 'Campaign Summary',

  /**
   * The identifier for the scenario summary.
   */
  SCENARIO_SUMMARY_ID: 'campaignSummary',

  NEW_MESSAGE_TABLE_COLUMNS: [
    {
      title: 'Step Name',
      key: 'stepName',
    },
    {
      title: 'Severity',
      key: 'severity',
    },
    {
      title: 'Message',
      key: 'message',
    },
  ] as TableColumnConfig[],

  MINION_STACKED_BAR_CHART_OPTIONS: {
    colors: [ColorsConfig.PRIMARY_COLOR_HEX_CODE, ColorsConfig.PURPLE_COLOR_HEX_CODE, ColorsConfig.GREY_2_HEX_CODE],
    chart: {
      type: 'bar',
      stacked: true,
      stackType: '100%',
      toolbar: {
        show: false,
      },
      zoom: {
        enabled: false,
      },
    },
    grid: {
      show: false,
      xaxis: {
        lines: {
          show: false,
        },
      },
      yaxis: {
        lines: {
          show: false,
        },
      },
      // Note: this is a workaround to remove unneeded paddings from the apex chart
      padding: {
        top: -18,
        right: 0,
        bottom: -72,
        left: -12,
      },
    },
    plotOptions: {
      bar: {
        horizontal: true,
      },
    },
    yaxis: {
      show: false,
      labels: {
        show: false,
      },
      axisTicks: {
        show: false,
      },
      axisBorder: {
        show: false,
      },
      crosshairs: {
        show: false,
      },
    },
    xaxis: {
      labels: {
        show: false,
      },
      axisTicks: {
        show: false,
      },
      axisBorder: {
        show: false,
      },
    },
    legend: {
      show: false,
    },
    fill: {
      opacity: 1,
    },
    dataLabels: {
      enabled: false,
    },
    tooltip: {
      enabled: false,
    },
    states: {
      hover: {
        filter: {
          type: 'none',
        },
      },
      active: {
        filter: {
          type: 'none',
        },
      },
    },
  } as ApexOptions,

  EXECUTION_STEP_DONUT_CHART_OPTIONS: {
    colors: [ColorsConfig.PRIMARY_COLOR_HEX_CODE, ColorsConfig.PINK_HEX_CODE],
    chart: {
      type: 'donut',
    },
    stroke: {
      width: 0,
    },
    plotOptions: {
      pie: {
        expandOnClick: false,
        donut: {
          size: '65%',
        },
      },
    },
    grid: {
      // Note: this is a workaround to remove unneeded paddings from the apex chart
      padding: {
        top: -2,
        right: -2,
        bottom: -8,
        left: -2,
      },
    },
    legend: {
      show: false,
    },
    fill: {
      opacity: 1,
    },
    dataLabels: {
      enabled: false,
    },
    tooltip: {
      enabled: false,
    },
    states: {
      hover: {
        filter: {
          type: 'none',
        },
      },
      active: {
        filter: {
          type: 'none',
        },
      },
    },
  } as ApexOptions,

  getScenarioChartOptions: (cumulativeMinionsCount: number): ApexOptions => {
    return {
      chart: {
        type: 'area',
        zoom: {
          enabled: false,
        },
      },
      dataLabels: {
        enabled: false,
      },
      stroke: {
        curve: 'straight',
        width: 0.5,
      },
      grid: {
        row: {
          colors: ['#fff', 'transparent'],
          opacity: 0,
        },
      },
      tooltip: {
        custom: function ({ series, seriesIndex, dataPointIndex, w }) {
          const xValue = w.globals.seriesX[seriesIndex][dataPointIndex]

          return `
            <div class="px-3 py-2 min-w-36 rounded-md bg-gray-900 text-white font-light border-none">
              <div class="pb-2 mb-2 border-b border-solid border-gray-500">
                <span>Duration: ${xValue}s</span>
              </div>
              <span>Minions: ${series[seriesIndex][dataPointIndex]}</span>
            </div>
          `
        },
      },
      xaxis: {
        decimalsInFloat: 0,
        type: 'numeric',
        tickAmount: 'dataPoints',
        title: {
          text: 'Duration, s',
          style: {
            cssClass: 'fill-gray-800 dark:fill-gray-100',
            fontWeight: 400,
          },
        },
        tooltip: {
          enabled: false,
        },
        labels: {
          style: {
            cssClass: 'fill-gray-800 dark:fill-gray-100',
          },
        },
      },
      yaxis: {
        decimalsInFloat: 0,
        tickAmount: 2,
        min: 0,
        /**
         * When the cumulative minion count is more than 10,
         * set the max value to be cumulative minion count + (cumulative minion count * 0.2)
         * to get some spaces for the y-axis.
         */
        max: cumulativeMinionsCount > 10 ? +cumulativeMinionsCount + cumulativeMinionsCount * 0.2 : 10,
        title: {
          text: 'Minions',
          offsetX: 0,
          offsetY: -40,
          style: {
            cssClass: 'fill-gray-800 dark:fill-gray-50',
            fontWeight: 400,
          },
        },
        labels: {
          style: {
            cssClass: 'fill-gray-800 dark:fill-gray-50',
            fontWeight: 400,
          },
        },
      },
    }
  },
}
