import type {ApexOptions} from 'apexcharts'
import tinycolor from 'tinycolor2'
import {SeriesDetailsConfig} from '~/utils/configs/series-details.config'

/**
 * Renders the outer panel and header bar shared by chart tooltips.
 * `headerHtml` goes in the top strip; `rowsHtml` is concatenated below.
 */
export function renderChartTooltipShell(headerHtml: string, rowsHtml: string[]): string {
  return `<div class="p-4 min-w-72 rounded-md bg-gray-900 text-white font-light">
        <div class="w-full flex justify-between items-center pb-3 border-b border-solid border-gray-50">
            ${headerHtml}
        </div>
        ${rowsHtml.join('')}
    </div>`
}

/**
 * Renders one tooltip row: a colored dot plus a flex-between body. `bodyHtml`
 * typically contains a left-side label and a right-side value.
 */
export function renderChartTooltipRow(color: string, bodyHtml: string): string {
  return `<div class="w-full flex items-center">
        <div class="w-3 h-3 mr-2 rounded-full border-2 border-solid border-white" style="background-color:${color}"></div>
        <div class="flex flex-grow justify-between items-center py-2 text-xs font-normal text-gray-200">
            ${bodyHtml}
        </div>
    </div>`
}

export interface BuildAggregationChartContext {
  pushSeries: (series: ApexDataSeries) => void
  pushStrokeDash: (value: number) => void
}

export interface BuildAggregationChartParams {
  aggregationResult: { [key: string]: TimeSeriesValues }
  dataSeries: DataSeries[]
  /** Used by the empty-state y-axis. */
  scheduledMinions?: number
  /** Factory receiving the summary map; returns the tooltip renderer. */
  tooltip?: (summaryBySeriesName: { [name: string]: string }) => (options: any) => string
  /** Runs once after defaults — for x-axis tweaks, etc. */
  customizeOptions?: (options: ApexOptions) => void
  /** Invoked per aggregation entry; push any series/stroke dashes via `ctx`. */
  buildSeries: (
    chartOptionData: ChartOptionData,
    values: TimeSeriesAggregationResult[],
    ctx: BuildAggregationChartContext,
  ) => void
}

export const ChartHelper = {
  toChartOptionData(key: string, dataSeries: DataSeries[]): ChartOptionData {
    const seriesDefinition = dataSeries.find((s) => s.reference === key)
    const fmt = getSeriesFormat(seriesDefinition, key)

    return {
      dataSeriesName: seriesDefinition?.displayName ?? key,
      dataSeriesColor:
        seriesDefinition?.color && tinycolor(seriesDefinition.color).isValid()
          ? seriesDefinition.color
          : ColorsConfig.PURPLE_COLOR_HEX_CODE,
      isDurationNanoField: fmt.isDurationNanoField,
      isMinionsCountSeries: fmt.isMinionsCountSeries,
      decimal: fmt.decimal,
      format: fmt.format,
    }
  },

  getDataSeries(
    chartOptionData: ChartOptionData,
    timeAggregationResults: TimeSeriesAggregationResult[],
  ): ApexDataSeries {
    return {
      name: chartOptionData.dataSeriesName,
      data: timeAggregationResults.map((v) => ({
        x: new Date(v.start).getTime(),
        y: chartOptionData.format(v.value),
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
          return `${val.toFixed(chartOptionData.decimal)}${chartOptionData.isDurationNanoField ? ' ms' : ''}`
        },
        style: {
          colors: [chartOptionData.dataSeriesColor],
          cssClass: 'apexcharts-yaxis-label',
        },
      },
      title: {
        text: chartOptionData.isMinionsCountSeries ? 'Minions count' : '',
        style: {
          cssClass: 'fill-gray-800 dark:fill-gray-50',
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
        color: '#e0e0e0',
      },
      labels: {
        show: true,
        style: {
          cssClass: 'fill-gray-800 dark:fill-gray-50',
        },
        formatter: (val: number, _: any) => val.toFixed(0),
      },
      title: {
        text: 'Minions Count',
        style: {
          cssClass: 'fill-gray-800 dark:fill-gray-50',
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

  /**
   * Shared skeleton for building an aggregation-based chart. Callers plug in
   * series/stroke-dash construction via `buildSeries`, and optional tooltip
   * or x-axis tweaks via `tooltip` / `customizeOptions`.
   */
  buildAggregationChart(params: BuildAggregationChartParams): ChartData {
    const aggregations = Object.entries(params.aggregationResult).filter(([, tsv]) => tsv.values.length)
    const chartOptions: ApexOptions = ChartsConfig.getDefaultChartOptions()

    params.customizeOptions?.(chartOptions)

    if (aggregations.length === 0) {
      if (params.tooltip) chartOptions.tooltip!.custom = params.tooltip({})
      chartOptions.yaxis = ChartHelper.getEmptyChartYAxisOptions(params.scheduledMinions)

      return { chartOptions, chartDataSeries: [] }
    }

    const chartDataSeries: ApexAxisChartSeries = []
    const yAxisConfigs: ApexYAxis[] = []
    const strokeDashArray: number[] = []
    const summaryBySeriesName: { [name: string]: string } = {}
    const yAxisAnnotations: any[] = []
    const ctx: BuildAggregationChartContext = {
      pushSeries: (series) => chartDataSeries.push(series),
      pushStrokeDash: (value) => strokeDashArray.push(value),
    }

    aggregations.forEach(([key, timeSeriesValues], idx) => {
      const chartOptionData = ChartHelper.toChartOptionData(key, params.dataSeries)
      params.buildSeries(chartOptionData, timeSeriesValues.values, ctx)
      yAxisConfigs.push(ChartHelper.getYAxisOptions(chartOptionData))

      if (timeSeriesValues.summary && key !== SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE) {
        const rawY = timeSeriesValues.summary.value
        summaryBySeriesName[chartOptionData.dataSeriesName] = chartOptionData.format(rawY)
        yAxisAnnotations.push({
          y: rawY,
          yAxisIndex: idx,
          borderColor: chartOptionData.dataSeriesColor,
          borderWidth: 1,
          strokeDashArray: 4,
          label: {text: ''},
        })
      }
    })

    if (params.tooltip) chartOptions.tooltip!.custom = params.tooltip(summaryBySeriesName)

    // Add annotations after chart mounts so Y-axis scales are fully computed.
    if (yAxisAnnotations.length > 0) {
      chartOptions.chart!.events = {
        mounted: (chart: any) => {
          yAxisAnnotations.forEach((anno) => chart.addYaxisAnnotation(anno))
        },
      }
    }

    chartOptions.yaxis = yAxisConfigs
    if (strokeDashArray.length) chartOptions.stroke!.dashArray = strokeDashArray

    return { chartOptions, chartDataSeries }
  },
}
