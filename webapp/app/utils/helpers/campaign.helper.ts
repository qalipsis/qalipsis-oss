import type {ApexOptions} from 'apexcharts'
import {eachDayOfInterval, format, isSameDay, sub} from 'date-fns'
import {DateTime} from 'luxon'

const defaultTagClass: TagStyleClass = {
  backgroundCssClass: 'bg-gray-100 dark:bg-gray-700',
  textCssClass: 'text-gray-700 dark:text-gray-100',
}

const tagClass: { [key in ExecutionStatus]: Tag } = {
  SUCCESSFUL: {
    text: 'Successful',
    backgroundCssClass: 'bg-green-100 dark:bg-green-800',
    textCssClass: 'text-green-600 dark:text-green-100',
  },
  WARNING: {
    text: 'Warning',
    backgroundCssClass: 'bg-yellow-100 dark:bg-yellow-800',
    textCssClass: 'text-yellow-600 dark:text-yellow-100',
  },
  FAILED: {
    text: 'Failed',
    backgroundCssClass: 'bg-red-100 dark:bg-red-800',
    textCssClass: 'text-red-600 dark:text-red-100',
  },
  ABORTED: {
    text: 'Aborted',
    backgroundCssClass: 'bg-red-100 dark:bg-red-800',
    textCssClass: 'text-red-600 dark:text-red-100',
  },
  SCHEDULED: {
    text: 'Scheduled',
    backgroundCssClass: 'bg-gray-100 dark:bg-gray-700',
    textCssClass: 'text-green-600 dark:text-green-100',
  },
  QUEUED: {
    text: 'Queued',
    backgroundCssClass: 'bg-purple-100 dark:bg-purple-800',
    textCssClass: 'text-purple-600 dark:text-purple-100',
  },
  IN_PROGRESS: {
    text: 'In progress',
    backgroundCssClass: 'bg-purple-100 dark:bg-purple-800',
    textCssClass: 'text-purple-600 dark:text-purple-100',
  },
}

const renderCampaignStaticTooltip: (options: any) => any = ({ series, seriesIndex, dataPointIndex, w }): string => {
  const statisticContent: string[] = []

  series.forEach((s: any, idx: number) => {
    if (idx === seriesIndex) {
      const seriesContent = `
        <div class="flex items-center justify-between gap-x-1">
          <div class="w-3 h-3 rounded-full" style="background-color:${w.globals.colors[idx]}"></div>
          <div class="flex items-center text-gray-950 dark:text-gray-200">
            <span>${w.globals.seriesNames[idx]}:</span>
            <span class="pl-1">${s[dataPointIndex]}</span>
          </div>
        </div>
      `
      statisticContent.push(seriesContent)
    }
  })

  return `
    <div class="px-4 py-2 min-w-36 rounded-md text-gray-950 dark:bg-gray-900 dark:text-white font-light border-none">
      <div class="w-full flex items-center pb-2 border-b border-solid border-gray-100 dark:border-gray-500">
         <span>${w.globals.labels[dataPointIndex]}</span>
      </div>
      <div class="w-full flex items-center my-2">
        ${statisticContent.join('')}
      </div>
    </div>
  `
}

const createCampaignDetailsChartTooltip =
    (summaryBySeriesName: { [name: string]: string }) =>
        ({seriesIndex, dataPointIndex, w}: any): string => {
          const dt =
              w.config.series[seriesIndex].data[dataPointIndex]?.x ||
              w.globals.initialSeries.filter((serie: any) => !!serie.data[dataPointIndex]?.x)[0].data[dataPointIndex].x
          const day = format(new Date(dt), 'yy-MM-dd')
          const time = format(new Date(dt), 'HH:mm:ss')
          const rows: string[] = []

          w.globals.initialSeries.forEach((series: { data: any[]; color: any; name: any }) =>
              series.data.forEach((point) => {
                if (point?.x !== dt) return
                const label =
                    series.name === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE ? 'Minions count' : series.name
                const summaryVal = summaryBySeriesName[series.name]
                rows.push(
                    renderChartTooltipRow(
                        series.color,
                        `<div class="pr-1">${label}:</div><div class="ml-2">${point.y}${series.name !== SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE && summaryVal !== undefined ? ` (${summaryVal})` : ''}</div>`,
                    ),
                )
              }),
          )

          const header = `<div class="text-sm font-normal">${day}</div><div class="text-sm font-normal">${time}</div>`

          return renderChartTooltipShell(header, rows)
        }

export const CampaignHelper = {
  toCampaignConfigForm(campaignConfig: CampaignConfiguration): CampaignConfigurationForm {
    const formattedTimeoutValue: FormattedTimeframe = campaignConfig.timeout
      ? TimeframeHelper.toFormattedTimeframe(campaignConfig.timeout)
      : { value: null, unit: TimeframeUnitConstant.MS }
    const timeoutType =
      formattedTimeoutValue.value === null
        ? TimeoutTypeConstant.NONE
        : campaignConfig.hardTimeout
          ? TimeoutTypeConstant.HARD
          : TimeoutTypeConstant.SOFT

    return {
      timeoutType: timeoutType,
      durationValue: formattedTimeoutValue.value ? formattedTimeoutValue.value.toString() : '',
      durationUnit: formattedTimeoutValue.unit,
      scheduled: !!campaignConfig.scheduledAt,
      repeatEnabled: !!campaignConfig.scheduling?.scheduling,
      repeatTimeRange: campaignConfig.scheduling?.scheduling ?? 'DAILY',
      repeatValues: campaignConfig.scheduling?.restrictions
        ? campaignConfig.scheduling?.restrictions.map((r) => r.toString())
        : [],
      relativeRepeatValues:
        campaignConfig.scheduling?.scheduling === 'MONTHLY' && campaignConfig.scheduling?.restrictions
          ? campaignConfig.scheduling?.restrictions.filter((restriction) => restriction < 0).map((r) => r.toString())
          : [],
      timezone: campaignConfig.scheduling?.timeZone ?? '',
      scheduledTime: campaignConfig.scheduledAt ? DateTime.fromISO(campaignConfig.scheduledAt).toJSDate() : null,
    }
  },

  toCampaignConfiguration(
    campaignName: string,
    campaignConfigForm: CampaignConfigurationForm,
    scenarioConfigFormMap: { [key: string]: ScenarioConfigurationForm },
  ): CampaignConfiguration {
    const scenarios: Record<string, ScenarioRequest> = Object.fromEntries(
      Object.entries(scenarioConfigFormMap).map(([key, form]) => [key, ScenarioHelper.toScenarioRequest(form)]),
    )

    const campaignConfiguration: CampaignConfiguration = {
      name: campaignName,
      speedFactor: 1,
      startOffsetMs: 1000,
      hardTimeout: campaignConfigForm.timeoutType === TimeoutTypeConstant.HARD,
      scenarios: scenarios,
    }

    if (campaignConfigForm.durationValue) {
      campaignConfiguration.timeout = TimeframeHelper.toIsoStringDuration(
        +campaignConfigForm.durationValue,
        campaignConfigForm.durationUnit,
      )
    }

    if (campaignConfigForm.scheduled) {
      campaignConfiguration.scheduledAt = DateTime.fromISO(campaignConfigForm.scheduledTime!.toISOString())
        .setZone(campaignConfigForm.timezone, { keepLocalTime: true })
        .toJSDate()
        .toISOString()
    }

    if (campaignConfigForm.repeatEnabled) {
      const restrictions =
        campaignConfigForm.repeatTimeRange === 'MONTHLY'
          ? [...campaignConfigForm.repeatValues, ...campaignConfigForm.relativeRepeatValues]
          : [...campaignConfigForm.repeatValues]
      campaignConfiguration.scheduling = {
        scheduling: campaignConfigForm.repeatTimeRange,
        timeZone: campaignConfigForm.timezone,
        restrictions: [...restrictions].map((restriction) => +restriction),
      }
    }

    return campaignConfiguration
  },

  toCampaignSummaryChartData(campaignSummary: CampaignSummaryResult[]): ChartData {
    const xAxisEndDate = new Date()
    const xAxisStartDate = sub(new Date(), { days: 6 })
    const xAxisDayIntervals = eachDayOfInterval({
      start: new Date(xAxisStartDate.getFullYear(), xAxisStartDate.getMonth(), xAxisStartDate.getDate()),
      end: new Date(xAxisEndDate.getFullYear(), xAxisEndDate.getMonth(), xAxisEndDate.getDate()),
    })
    /**
     * This list maps to the week day index from the Date().
     *
     * E.g.,
     * The current date is on Sunday:
     * The result from new Date().getDay() will be 0
     */
    const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
    const xAxisCategories = xAxisDayIntervals.map((date) => daysOfWeek[date.getDay()])

    const chartOptions: ApexOptions = {
      chart: {
        type: 'bar',
        stacked: true,
        toolbar: {
          show: false,
        },
      },
      colors: [ColorsConfig.PRIMARY_COLOR_HEX_CODE, ColorsConfig.GREY_2_HEX_CODE],
      plotOptions: {
        bar: {
          horizontal: false,
          borderRadius: 4,
        },
      },
      xaxis: {
        categories: xAxisCategories,
        labels: {
          style: {
            cssClass: 'fill-gray-800 dark:fill-gray-100',
          },
        },
      },
      yaxis: {
        show: false,
      },
      tooltip: {
        custom: renderCampaignStaticTooltip,
      },
      dataLabels: {
        enabled: false,
      },
      legend: {
        show: false,
      },
      grid: {
        show: false,
      },
      states: {
        hover: {
          filter: {
            type: 'none',
          },
        },
      },
    }
    const successfulDataSeries: number[] = []
    const failedDataSeries: number[] = []

    if (campaignSummary?.length > 0) {
      xAxisDayIntervals.forEach((day) => {
        const daySummary = campaignSummary.find((summary) => isSameDay(new Date(summary.start), day))
        successfulDataSeries.push(daySummary?.successful ?? 0)
        failedDataSeries.push(daySummary?.failed ?? 0)
      })
    }

    return {
      chartOptions: chartOptions,
      chartDataSeries: [
        {
          name: 'successful',
          data: successfulDataSeries,
        },
        {
          name: 'failed',
          data: failedDataSeries,
        },
      ],
    }
  },

  toChartData(
      aggregationResult: { [key: string]: TimeSeriesValues },
    dataSeries: DataSeries[],
    campaignExecutionDetails: CampaignExecutionDetails,
  ): ChartData {
    return ChartHelper.buildAggregationChart({
      aggregationResult,
      dataSeries,
      scheduledMinions: campaignExecutionDetails.scheduledMinions,
      tooltip: createCampaignDetailsChartTooltip,
      buildSeries: (chartOptionData, values, ctx) => {
        ctx.pushSeries(ChartHelper.getDataSeries(chartOptionData, values))
      },
    })
  },

  toTableData(campaigns: Campaign[]): CampaignTableData[] {
    return campaigns.map((campaign) => ({
      ...campaign,
      scenarioText: campaign.scenarios.map((scenario) => scenario.name).join(','),
      creationTime: TimeframeHelper.toSpecificFormat(new Date(campaign.creation), 'dd/MM/yyyy, HH:mm:ss'),
      startTime: campaign.start
        ? TimeframeHelper.toSpecificFormat(new Date(campaign.start), 'dd/MM/yyyy, HH:mm:ss')
        : 'Not started yet',
      /**
       * Only shows the elapsed time when the status is scheduled,
       * or the status is aborted and there is no end time for the campaign.
       */
      elapsedTime:
        campaign.status === 'SCHEDULED' || (campaign.status === 'ABORTED' && !campaign.end)
          ? '-'
          : TimeframeHelper.elapsedTime(
              new Date(campaign.creation),
              campaign.end ? new Date(campaign.end) : new Date(),
            ),
      statusTag: campaign.status ? CampaignHelper.toExecutionStatusTag(campaign.status) : null,
    }))
  },

  toExecutionStatusTag(executionStatus: ExecutionStatus): Tag {
    const tag: Tag = tagClass[executionStatus] ?? {
      text: executionStatus,
      backgroundCssClass: defaultTagClass.backgroundCssClass,
      textCssClass: defaultTagClass.textCssClass,
    }

    return tag
  },
}
