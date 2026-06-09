import {format} from 'date-fns'

const createReportChartTooltip =
    (summaryBySeriesName: { [name: string]: string }) =>
        ({seriesIndex, dataPointIndex, w}: any): string => {
          const elapsedTime = w.config.series[seriesIndex].data[dataPointIndex]?.x
          const rows: string[] = []

          w.globals.initialSeries.forEach((series: { data: any[]; name: string; color: any }) =>
              series.data.forEach((point) => {
                if (point?.x !== elapsedTime) return
                const day = format(new Date(point.meta), 'yy-MM-dd')
                const time = format(new Date(point.meta), 'HH:mm:ss')
                const campaignName = series.name.substring(series.name.indexOf('{') + 1, series.name.lastIndexOf('}'))
                const seriesName = series.name.replace(`{${campaignName}}`, '')
                const summaryVal = summaryBySeriesName[seriesName]
                rows.push(
                    renderChartTooltipRow(
                        series.color,
                        `<div>
                          <div>${day}, ${time}:</div>
                          <div class="pr-1">${campaignName} - ${seriesName}</div>
                      </div>
                      <div class="ml-2">${point.y}${summaryVal !== undefined ? ` [${summaryVal}]` : ''}</div>`,
                    ),
                )
              }),
          )

          return renderChartTooltipShell(`<span>Elapsed time: ${elapsedTime} s</span>`, rows)
        }

export const ReportHelper = {
  toReportTableData(reports: DataReport[]): ReportTableData[] {
    return reports.map((report) => ({
      ...report,
      description: report.description ?? '-',
      creator: report.creator ?? '-',
      concatenatedCampaignNames: report.resolvedCampaigns?.map((campaign) => campaign.name)?.join(','),
    }))
  },

  toReportChartData(
      timeSeriesAggregationResult: { [seriesReference: string]: TimeSeriesValues },
    dataSeries: DataSeries[],
    campaignOptions: CampaignOption[],
  ): ChartData {
    const campaignKeyToOption = keyBy(campaignOptions, 'key')
    const maxScheduledMinions = Math.max(0, ...campaignOptions.map((c) => c.scheduledMinions ?? 0))

    return ChartHelper.buildAggregationChart({
      aggregationResult: timeSeriesAggregationResult,
      dataSeries,
      scheduledMinions: maxScheduledMinions,
      tooltip: createReportChartTooltip,
      customizeOptions: (options) => {
        options.xaxis!.type = 'numeric'
        options.xaxis!.tooltip!.enabled = false
        options.xaxis!.labels!.formatter = (value) => {
          const sec = Math.floor(Number(value))
          const h = Math.floor(sec / 3600)
          const m = Math.floor((sec % 3600) / 60)
          const s = sec % 60

          return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':')
        }
      },
      buildSeries: (chartOptionData, values, ctx) => {
        const campaignChartSeriesMap = values.reduce<{ [key: string]: TimeSeriesAggregationResult[] }>((acc, cur) => {
          ;(acc[cur.campaign] ??= []).push(cur)

          return acc
        }, {})

        Object.entries(campaignChartSeriesMap).forEach(([campaignKey, campaignSeriesValues]) => {
          const campaignOption = campaignKeyToOption[campaignKey]!
          ctx.pushSeries({
            name: `${chartOptionData.dataSeriesName}{${campaignOption.name}}`,
            data: campaignSeriesValues.map((v) => ({
              meta: new Date(v.start).getTime(),
              x: TimeframeHelper.isoStringToTargetTimeframeUnit(v.elapsed, 'SEC'),
              y: chartOptionData.format(v.value),
            })),
            color: chartOptionData.dataSeriesColor,
          })
          ctx.pushStrokeDash(campaignOption.strokeDashArray)
        })
      },
    })
  },

  toReportDetailsTableData(
      timeSeriesAggregationResult: { [seriesReference: string]: TimeSeriesValues },
    dataSeries: DataSeries[],
    campaigns: CampaignOption[],
  ): ReportDetailsTableData[] {
    const dataSeriesReferenceToDataSeries = keyBy(dataSeries, 'reference')
    const campaignKeyToName: Record<string, string> = Object.fromEntries(campaigns.map((c) => [c.key, c.name]))

    return Object.keys(timeSeriesAggregationResult).reduce<ReportDetailsTableData[]>((acc, cur) => {
      const reportTableDataComponents: ReportDetailsTableData[] = timeSeriesAggregationResult[
        cur
          ]!.values.map<ReportDetailsTableData>((res) => {
        const formattedAggregatedValue = TimeSeriesHelper.toComposedValue(
          dataSeriesReferenceToDataSeries[cur]!,
          res.value,
        )
        const reportTableData: ReportDetailsTableData = {
          id: `${res.campaign}_${cur}`,
          seriesReference: cur,
          seriesName: dataSeriesReferenceToDataSeries[cur]!.displayName,
          campaignKey: res.campaign,
          campaignName: campaignKeyToName[res.campaign]!,
          startTime: res.start,
          elapsed: TimeframeHelper.isoStringToTargetTimeframeUnit(res.elapsed, 'SEC'),
          elapsedText: `${TimeframeHelper.isoStringToTargetTimeframeUnit(res.elapsed, 'SEC')} s`,
          value: res.value,
          startTimeText: TimeframeHelper.toSpecificFormat(new Date(res.start), 'dd/MM/yyyy, HH:mm:ss'),
          valueDisplayText: formattedAggregatedValue.formattedText,
        }

        return reportTableData
      })
      acc.push(...reportTableDataComponents)

      return acc
    }, [])
  },
}
