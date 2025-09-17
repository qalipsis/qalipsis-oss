import {format} from "date-fns";
import {
    type ExecutionProfileStage,
    type ReportMessage,
    type ReportMessageSeverity,
    type ScenarioConfigurationForm,
    type ScenarioReport,
    type ScenarioRequest,
    type Stage,
    StageExternalExecutionProfileConfiguration,
    type ZoneForm,
} from "../types/scenario";
import type {TagStyleClass} from "../types/common";

const defaultTagClass: TagStyleClass = {
    backgroundCssClass: "bg-gray-100 dark:bg-gray-700",
    textCssClass: "text-gray-700 dark:text-gray-100",
}

const tagClass: { [key in ReportMessageSeverity]: TagStyleClass } = {
    INFO: {
        backgroundCssClass: "bg-purple-100 dark:bg-purple-600",
        textCssClass: "text-purple-600 dark:text-purple-100",
    },
    WARN: {
        backgroundCssClass: "bg-yellow-100 dark:bg-yellow-400",
        textCssClass: "text-yellow-400 dark:text-yellow-100",
    },
    ERROR: {
        backgroundCssClass: "bg-red-100 dark:bg-red-600",
        textCssClass: "text-red-600 dark:text-red-100",
    },
    ABORT: {
        backgroundCssClass: "bg-red-100 dark:bg-red-600",
        textCssClass: "text-red-600 dark:text-red-100",
    }
}

export class ScenarioHelper {
    static toScenarioConfigForm(campaignConfiguration: CampaignConfiguration): {
        [key: string]: ScenarioConfigurationForm;
    } {
        const scenarioKeyToScenarioRequest = campaignConfiguration.scenarios;

        return Object.keys(campaignConfiguration.scenarios).reduce<{
            [key: string]: ScenarioConfigurationForm;
        }>((acc, cur) => {
            const executionProfile = scenarioKeyToScenarioRequest[cur]
                .executionProfile as StageExternalExecutionProfileConfiguration;
            const zones = scenarioKeyToScenarioRequest[cur].zones;
            const stages = executionProfile.stages.map<ExecutionProfileStage>(
                (stage) => ({
                    resolution: stage.resolutionMs,
                    minionsCount: stage.minionsCount,
                    startDuration: stage.rampUpDurationMs,
                    duration: stage.totalDurationMs,
                })
            );
            const zoneForms: ZoneForm[] = zones
                ? Object.keys(zones).map((zoneName) => ({
                    name: zoneName,
                    share: zones[zoneName],
                }))
                : [];
            acc[cur] = {
                executionProfileStages: stages,
                zones: zoneForms,
            };

            return acc;
        }, {});
    }

    static toScenarioRequest(
        scenarioConfigForm: ScenarioConfigurationForm
    ): ScenarioRequest {
        const totalMinionsCount = scenarioConfigForm.executionProfileStages.reduce(
            (acc, cur) => {
                acc += +cur.minionsCount;
                return acc;
            },
            0
        );
        const stages: Stage[] = scenarioConfigForm.executionProfileStages.map(
            (executionProfileStage) => {
                return {
                    minionsCount: +executionProfileStage.minionsCount,
                    rampUpDurationMs: +executionProfileStage.startDuration,
                    totalDurationMs: +executionProfileStage.duration,
                    resolutionMs: +executionProfileStage.resolution,
                };
            }
        );

        const scenarioRequest: ScenarioRequest = {
            minionsCount: totalMinionsCount,
            executionProfile: new StageExternalExecutionProfileConfiguration(
                stages,
                "GRACEFUL"
            ),
        };

        if (scenarioConfigForm.zones.length > 0) {
            const zones = scenarioConfigForm.zones.reduce<{ [key: string]: number }>(
                (acc, cur) => {
                    acc[cur.name] = cur.share;

                    return acc;
                },
                {}
            );
            scenarioRequest.zones = zones;
        }

        return scenarioRequest;
    }

    /**
     * Gets the scenario reports for the UI.
     *
     * @param selectedScenarioNames The selected scenario names
     * @param campaignExecutionDetails The campaign execution details.
     * @returns the list of scenario reports for the UI.
     */
    static getSelectedScenarioReports(
        selectedScenarioNames: string[],
        campaignExecutionDetails: CampaignExecutionDetails
    ): ScenarioReport[] {
        const reports = campaignExecutionDetails.scenariosReports
            .filter((scenarioReport) =>
                selectedScenarioNames.includes(scenarioReport.name)
            )
            .map<ScenarioReport>((scenariosReport) => {
                const scenario = campaignExecutionDetails.scenarios?.find(
                    (s) => s.name === scenariosReport.name
                );

                return {
                    id: scenariosReport.id,
                    name: scenariosReport.name,
                    start: scenariosReport.start,
                    end: scenariosReport.end,
                    status: scenariosReport.status,
                    scheduledMinions: scenario?.minionsCount ?? 0,
                    startedMinions: scenariosReport.startedMinions ?? 0,
                    completedMinions: scenariosReport.completedMinions ?? 0,
                    successfulExecutions: scenariosReport.successfulExecutions ?? 0,
                    failedExecutions: scenariosReport.failedExecutions ?? 0,
                    messages: scenariosReport.messages,
                };
            });

        const shouldScenarioSummaryReportIncluded =
            campaignExecutionDetails.scenarios &&
            campaignExecutionDetails.scenarios.length > 1 &&
            selectedScenarioNames.length ===
            campaignExecutionDetails.scenarios?.length;

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
                const reportMessages = cur.messages?.length ? cur.messages : [];

                return acc.concat(...reportMessages);
            }, []),
        };

        /**
         * When there is only one scenario report, the summary report is set as the only one report.
         */
        return shouldScenarioSummaryReportIncluded
            ? [scenarioSummaryReport, ...reports]
            : reports;
    }

    /**
     * Converts the severity to a tag.
     *
     * @param severity The severity of the report message.
     * @returns A tag for displaying the severity on the table.
     */
    static toSeverityTag(severity: ReportMessageSeverity): Tag {
        return {
            backgroundCssClass: tagClass[severity].backgroundCssClass
                ?? defaultTagClass.backgroundCssClass,
            textCssClass: tagClass[severity].textCssClass
                ?? defaultTagClass.textCssClass,
            text: severity
        }
    }

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
    static toTimeDisplayText(start: string, end?: string): string {
        const startDate = new Date(start);
        const endDate = end ? new Date(end) : new Date();

        const intervalInMilliseconds = endDate.getTime() - startDate.getTime();
        const intervalInHHMMSSFormat = TimeframeHelper.milliSecondsInHHMMSSFormat(
            intervalInMilliseconds
        );

        const hasIdenticalDate =
            startDate.toDateString() === endDate.toDateString();

        try {
            const startDateText = format(startDate, "MM/dd/yy, HH:mm:ss");
            const endDateFormat = hasIdenticalDate
                ? "HH:mm:ss"
                : "MM/dd/yy, HH:mm:ss";
            const endDateText = format(endDate, endDateFormat);

            return end
                ? `${startDateText} -> ${endDateText}(${intervalInHHMMSSFormat})`
                : `${startDateText}(${intervalInHHMMSSFormat})`;
        } catch (error) {
            console.error(error);
            return "";
        }
    }

    /**
     * Gets the chart series to be displayed.
     *
     * @param executionProfileStages the execution profiles from the scenario config.
     *
     * @returns the chart data series to be displayed
     */
    static toScenarioConfigChartData(
        executionProfileStages: ExecutionProfileStage[]
    ): ChartData {
        const chartOptions = {...ScenarioDetailsConfig.CHART_OPTIONS};
        const chartDatSeries: { x: number; y: number }[] = [{x: 0, y: 0}];
        let cumulativeDuration = 0;
        let cumulativeMinionsCount = 0;

        executionProfileStages.forEach((executionProfileStage) => {
            cumulativeMinionsCount += +executionProfileStage.minionsCount;

            if (
                executionProfileStage.duration === executionProfileStage.startDuration
            ) {
                cumulativeDuration += +executionProfileStage.duration;
                const cumulativeDurationInSeconds = cumulativeDuration / 1000;
                chartDatSeries.push({
                    x: cumulativeDurationInSeconds,
                    y: cumulativeMinionsCount,
                });
            } else {
                const cumulativeStartDurationInSeconds =
                    (+executionProfileStage.startDuration + cumulativeDuration) / 1000;
                const cumulativeDurationInSeconds =
                    (+executionProfileStage.duration + cumulativeDuration) / 1000;
                cumulativeDuration += +executionProfileStage.duration;
                chartDatSeries.push({
                    x: cumulativeStartDurationInSeconds,
                    y: cumulativeMinionsCount,
                });
                chartDatSeries.push({
                    x: cumulativeDurationInSeconds,
                    y: cumulativeMinionsCount,
                });
            }
        });

        // When the cumulative minion count is more than 10
        if (cumulativeMinionsCount > 10) {
            chartOptions.yaxis = {
                // Sets the max value from the y-axis to be
                max:
                    cumulativeMinionsCount > 10
                        ? +cumulativeMinionsCount + cumulativeMinionsCount * 0.2
                        : 10,
                tickAmount: 2,
                title: {
                    text: "Minions",
                    offsetX: 0,
                    offsetY: -40,
                    style: {
                        color: "#ddd",
                        fontSize: "12px",
                        fontWeight: 400,
                    },
                },
            };
        }

        return {
            chartOptions: chartOptions,
            chartDataSeries: [
                {
                    name: "Minions",
                    data: chartDatSeries,
                },
            ],
        };
    }

    /**
     * Calculate the minion data series for the stacked bar chart.
     *
     * @param completedMinions the number of completed minions
     * @param startedMinions the number of started minions
     * @param scheduledMinions the number of scheduled minions
     *
     * @returns the data series for the stacked bar chart
     */
    static toMinionBarChartSeries(
        completedMinions: number,
        startedMinions: number,
        scheduledMinions: number
    ): ApexAxisChartSeries {
        // The bar chart length for the completed minions is the same as the number of completed minions
        const completedMinionsBarChartLength = completedMinions;

        // The bar chart length for the started minions equals to the number of started minions minus the number of completed minions.
        const startedMinionsBarChartLength = startedMinions - completedMinions;

        // The bar chart length for the scheduled minions equals to the number of scheduled minions minus the number of started minions
        const scheduledMinionsBarChartLength = scheduledMinions - startedMinions;

        return [
            {
                name: "Completed Minions",
                data: [
                    {
                        x: 0,
                        y: completedMinionsBarChartLength,
                    },
                ],
            },
            {
                name: "Started Minions",
                data: [
                    {
                        x: 0,
                        y: startedMinionsBarChartLength,
                    },
                ],
            },
            {
                name: "Scheduled Minions",
                data: [
                    {
                        x: 0,
                        y: scheduledMinionsBarChartLength,
                    },
                ],
            },
        ];
    }
}
