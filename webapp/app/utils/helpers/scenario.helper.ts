import {format} from 'date-fns'

const tagClass: { [key in ReportMessageSeverity]: TagStyleClass } = {
  INFO: {
    backgroundCssClass: 'bg-purple-100 dark:bg-purple-600',
    textCssClass: 'text-purple-600 dark:text-purple-100',
  },
  WARN: {
    backgroundCssClass: 'bg-yellow-100 dark:bg-yellow-400',
    textCssClass: 'text-yellow-400 dark:text-yellow-100',
  },
  ERROR: {
    backgroundCssClass: 'bg-red-100 dark:bg-red-600',
    textCssClass: 'text-red-600 dark:text-red-100',
  },
  ABORT: {
    backgroundCssClass: 'bg-red-100 dark:bg-red-600',
    textCssClass: 'text-red-600 dark:text-red-100',
  },
}

export const ScenarioHelper = {
  toScenarioConfigForm(campaignConfiguration: CampaignConfiguration): {
    [key: string]: ScenarioConfigurationForm
  } {
    if (!campaignConfiguration?.scenarios) return {}

    return Object.keys(campaignConfiguration.scenarios).reduce<{
      [key: string]: ScenarioConfigurationForm
    }>((acc, cur) => {
      const scenario = campaignConfiguration.scenarios[cur]
      if (!scenario) return acc

      const executionProfile = scenario.executionProfile
      if (!executionProfile || !('stages' in executionProfile)) return acc

      const profile = executionProfile as StageExternalExecutionProfileConfiguration

      const stages = (profile.stages ?? []).map<ExecutionProfileStage>((stage) => {
        const formattedRampUp = TimeframeHelper.msToFormattedTimeframe(stage.rampUpDurationMs)
        const formattedDuration = TimeframeHelper.msToFormattedTimeframe(stage.totalDurationMs)

        return {
          resolution: stage.resolutionMs,
          minionsCount: stage.minionsCount,
          rampUpDuration: formattedRampUp.value ?? stage.rampUpDurationMs,
          rampUpDurationUnit: formattedRampUp.unit,
          duration: formattedDuration.value ?? stage.totalDurationMs,
          durationUnit: formattedDuration.unit,
        }
      })

      const zoneForms: ZoneForm[] = scenario.zones
        ? Object.keys(scenario.zones).map((zoneName) => ({
            name: zoneName,
            share: scenario.zones![zoneName] ?? 0,
          }))
        : []

      acc[cur] = {
        executionProfileStages: stages,
        zones: zoneForms,
      }

      return acc
    }, {})
  },

  toScenarioRequest(scenarioConfigForm: ScenarioConfigurationForm): ScenarioRequest {
    const totalMinionsCount = scenarioConfigForm.executionProfileStages.reduce((acc, cur) => {
      acc += +cur.minionsCount

      return acc
    }, 0)
    const toMs = (value: number, unit?: TimeframeUnit): number => (unit ? TimeframeHelper.toMs(value, unit) : value)
    const stages: Stage[] = scenarioConfigForm.executionProfileStages.map((executionProfileStage) => {
      return {
        minionsCount: +executionProfileStage.minionsCount,
        rampUpDurationMs: toMs(+executionProfileStage.rampUpDuration, executionProfileStage.rampUpDurationUnit),
        totalDurationMs: toMs(+executionProfileStage.duration, executionProfileStage.durationUnit),
        resolutionMs: +executionProfileStage.resolution,
      }
    })

    const scenarioRequest: ScenarioRequest = {
      minionsCount: totalMinionsCount,
      executionProfile: new StageExternalExecutionProfileConfiguration(stages, 'GRACEFUL'),
    }

    if (scenarioConfigForm.zones.length > 0) {
      scenarioRequest.zones = Object.fromEntries(
        scenarioConfigForm.zones.map((zone) => [zone.name, zone.share]),
      )
    }

    return scenarioRequest
  },

  /**
   * Gets the scenario reports for the UI.
   *
   * @param selectedScenarioNames The selected scenario names
   * @param campaignExecutionDetails The campaign execution details.
   * @returns the list of scenario reports for the UI.
   */
  getSelectedScenarioReports(
    selectedScenarioNames: string[],
    campaignExecutionDetails: CampaignExecutionDetails,
  ): ScenarioReport[] {
      const reports = campaignExecutionDetails.scenarios
      .filter((scenarioReport) => selectedScenarioNames.includes(scenarioReport.name))
      .map<ScenarioReport>((scenariosReport) => {
        return {
          id: scenariosReport.id,
          name: scenariosReport.name,
          start: scenariosReport.start,
          end: scenariosReport.end,
          status: scenariosReport.status,
            scheduledMinions: scenariosReport.scheduledMinions ?? 0,
          startedMinions: scenariosReport.startedMinions ?? 0,
          completedMinions: scenariosReport.completedMinions ?? 0,
          successfulExecutions: scenariosReport.successfulExecutions ?? 0,
          failedExecutions: scenariosReport.failedExecutions ?? 0,
          messages: scenariosReport.messages,
            steps: scenariosReport.steps ?? [],
            meters: scenariosReport.meters ?? [],
            zoneDistribution: scenariosReport.zoneDistribution ?? {},
        }
      })

    const shouldScenarioSummaryReportIncluded =
      campaignExecutionDetails.scenarios.length > 1 &&
        selectedScenarioNames.length === campaignExecutionDetails.scenarios.length

    /**
     * The summary report for all scenarios.
     */
    const scenarioSummaryReport: ScenarioReport = {
      id: ScenarioDetailsConfig.SCENARIO_SUMMARY_ID,
      name: ScenarioDetailsConfig.SCENARIO_SUMMARY_NAME,
      start: campaignExecutionDetails.start,
      end: campaignExecutionDetails.end,
      status: campaignExecutionDetails.status,
      scheduledMinions: campaignExecutionDetails.scheduledMinions ?? 0,
      startedMinions: campaignExecutionDetails.startedMinions ?? 0,
      completedMinions: campaignExecutionDetails.completedMinions ?? 0,
      successfulExecutions: campaignExecutionDetails.successfulExecutions ?? 0,
      failedExecutions: campaignExecutionDetails.failedExecutions ?? 0,
      failureReason: campaignExecutionDetails.failureReason,
      messages: reports.reduce<ReportMessage[]>((acc, cur) => {
        const reportMessages = cur.messages?.length ? cur.messages : []

        return acc.concat(...reportMessages)
      }, []),
        steps: [],
        meters: campaignExecutionDetails.meters ?? [],
        zoneDistribution: {},
    }

    return shouldScenarioSummaryReportIncluded ? [scenarioSummaryReport, ...reports] : reports
  },

  /**
   * Converts the severity to a tag.
   *
   * @param severity The severity of the report message.
   * @returns A tag for displaying the severity on the table.
   */
  toSeverityTag(severity: ReportMessageSeverity): Tag {
    return {
      ...tagClass[severity],
      text: severity,
    }
  },

  /**
   * The time display text for the campaign
   *
   * @param start The start date time in ISO string format
   * @param end The end date time in ISO string format
   * @returns The formatted time display text
   *
   * @example
   * toTimeDisplayText('2023-02-20T19:18:09.482991Z', '2023-02-22T19:18:19.820797Z') -> 02/20/23, 20:18:09 -> 02/22/23, 20:18:19(00:00:11)
   * toTimeDisplayText('2023-02-20T19:18:09.482991Z', '2023-02-20T19:18:19.820797Z') -> 02/20/23, 20:18:09 -> 20:18:19(00:00:11)
   * toTimeDisplayText('2023-02-20T19:18:09.482991Z', null) -> 02/20/23, 20:18:09 (00:00:30)
   */
  toTimeDisplayText(start: string, end?: string): string {
    const startDate = new Date(start)
    const endDate = end ? new Date(end) : new Date()

    try {
      const hasIdenticalDate = startDate.toDateString() === endDate.toDateString()
      const startDateText = format(startDate, 'MM/dd/yy, HH:mm:ss')
      const endDateFormat = hasIdenticalDate ? 'HH:mm:ss' : 'MM/dd/yy, HH:mm:ss'
      const endDateText = format(endDate, endDateFormat)

      return end ? `${startDateText} ➞ ${endDateText}` : `${startDateText}`
    } catch (error) {
      console.error(error)
      return ''
    }
  },

  toIntervalInHHMMSSFormat(start: string, end?: string): string {
    const startDate = new Date(start)
    const endDate = end ? new Date(end) : new Date()
    const intervalInMilliseconds = endDate.getTime() - startDate.getTime()

    return TimeframeHelper.milliSecondsInHHMMSSFormat(intervalInMilliseconds)
  },

  /**
   * Gets the chart series to be displayed.
   *
   * @param executionProfileStages the execution profiles from the scenario config.
   * @returns the chart data series to be displayed
   */
  toScenarioConfigChartData(executionProfileStages: ExecutionProfileStage[]): ChartData {
    const chartDataSeries: { x: number; y: number }[] = [{ x: 0, y: 0 }]
    let cumulativeDuration = 0
    let cumulativeMinionsCount = 0

    executionProfileStages.forEach((executionProfileStage) => {
      cumulativeMinionsCount += +executionProfileStage.minionsCount

      if (executionProfileStage.duration === executionProfileStage.rampUpDuration) {
        cumulativeDuration += +executionProfileStage.duration
        const cumulativeDurationInSeconds = cumulativeDuration / 1000
        chartDataSeries.push({
          x: cumulativeDurationInSeconds,
          y: cumulativeMinionsCount,
        })
      } else {
        const cumulativeStartDurationInSeconds = (+executionProfileStage.rampUpDuration + cumulativeDuration) / 1000
        const cumulativeDurationInSeconds = (+executionProfileStage.duration + cumulativeDuration) / 1000
        cumulativeDuration += +executionProfileStage.duration
        chartDataSeries.push({
          x: cumulativeStartDurationInSeconds,
          y: cumulativeMinionsCount,
        }, {
          x: cumulativeDurationInSeconds,
          y: cumulativeMinionsCount,
        })
      }
    })

    const chartOptions = ScenarioDetailsConfig.getScenarioChartOptions(cumulativeMinionsCount)

    return {
      chartOptions: chartOptions,
      chartDataSeries: [
        {
          name: 'Minions',
          data: chartDataSeries,
        },
      ],
    }
  },

  /**
   * Calculate the minion data series for the stacked bar chart.
   *
   * @param completedMinions the number of completed minions
   * @param startedMinions the number of started minions
   * @param scheduledMinions the number of scheduled minions
   * @returns the data series for the stacked bar chart
   */
  toMinionBarChartSeries(
    completedMinions: number,
    startedMinions: number,
    scheduledMinions: number,
  ): ApexAxisChartSeries {
    const completedMinionsBarChartLength = completedMinions
    const startedMinionsBarChartLength = startedMinions - completedMinions
    const scheduledMinionsBarChartLength = scheduledMinions - startedMinions

    return [
      {
        name: 'Completed Minions',
        data: [{ x: 0, y: completedMinionsBarChartLength }],
      },
      {
        name: 'Started Minions',
        data: [{ x: 0, y: startedMinionsBarChartLength }],
      },
      {
        name: 'Scheduled Minions',
        data: [{ x: 0, y: scheduledMinionsBarChartLength }],
      },
    ]
  },
}
