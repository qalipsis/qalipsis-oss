import { ApexOptions } from "apexcharts";
import { format } from "date-fns";

export class ScenarioHelper {
    static SCENARIO_SUMMARY_NAME = 'Campaign Summary';
    static SCENARIO_SUMMARY_ID = 'campaignSummary';
    static getMessageTableColumnConfigs() {
        return [{
            title: 'Step Name',
            dataIndex: 'stepName',
            key: 'displayName',
        },
        {
            title: 'Severity',
            dataIndex: 'severity',
            key: 'severity',
        },
        {
            title: 'Message',
            dataIndex: 'message',
            key: 'message',
        }];
    }

    static MINION_STACKED_BAR_CHART_OPTIONS: ApexOptions = {
        colors: [ColorHelper.PRIMARY_COLOR_HEX_CODE, ColorHelper.PURPLE_COLOR_HEX_CODE, ColorHelper.GREY_2_HEX_CODE],
        chart: {
            type: 'bar',
            stacked: true,
            stackType: '100%',
            toolbar: {
                show: false
            },
            zoom: {
                enabled: false
            }
        },
        grid: {
            show: false,
            xaxis: {
                lines: {
                    show: false
                },
            },
            yaxis: {
                lines: {
                    show: false
                },
            },
            // Note: this is a workaround to remove unneeded paddings from the apex chart
            padding: {
                top: -18,
                right: 0,
                bottom: -72,
                left: -12
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
                show: false
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
                show: false
            }
        },
        legend: {
            show: false,
        },
        fill: {
            opacity: 1
        },
        dataLabels: {
            enabled: false
        },
        tooltip: {
            enabled: false
        },
        states: {
            hover: {
                filter: {
                    type: 'none'
                }
            },
            active: {
                filter: {
                    type: 'none'
                }
            },
        }
    }

    static EXECUTION_STEP_DONUT_CHART_OPTIONS: ApexOptions = {
        colors: [ColorHelper.PRIMARY_COLOR_HEX_CODE, ColorHelper.PINK_HEX_CODE],
        chart: {
            type: 'donut',
        },
        stroke: {
            width: 0
        },
        plotOptions: {
            pie: {
                expandOnClick: false,
                donut: {
                    size: '65%',
                }
            }
        },
        grid: {
            // Note: this is a workaround to remove unneeded paddings from the apex chart
            padding: {
                top: -2,
                right: -2,
                bottom: -8,
                left: -2
            },
        },
        legend: {
            show: false,
        },
        fill: {
            opacity: 1
        },
        dataLabels: {
            enabled: false
        },
        tooltip: {
            enabled: false
        },
        states: {
            hover: {
                filter: {
                    type: 'none'
                }
            },
            active: {
                filter: {
                    type: 'none'
                }
            },
        }
    }

    /**
     * Gets the scenario reports for the UI.
     * 
     * @param selectedScenarioNames The selected scenario names
     * @param campaignExecutionDetails The campaign execution details.
     * @returns the list of scenario reports for the UI.
     */
    static getSelectedScenarioReports(selectedScenarioNames: string[], campaignExecutionDetails: CampaignExecutionDetails): ScenarioReport[] {
        const reports = campaignExecutionDetails.scenariosReports
            .filter(scenarioReport => selectedScenarioNames.includes(scenarioReport.name))
            .map<ScenarioReport>(scenariosReport => {
                const scenario = campaignExecutionDetails.scenarios?.find(s => s.name === scenariosReport.name)

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
                    messages: scenariosReport.messages
                }
            });

        const shouldScenarioSummaryReportIncluded = campaignExecutionDetails.scenarios
            && campaignExecutionDetails.scenarios.length > 1
            && selectedScenarioNames.length === campaignExecutionDetails.scenarios?.length;

        /**
         * The summary report for all scenarios.
         */
        const scenarioSummaryReport: ScenarioReport = {
            id: ScenarioHelper.SCENARIO_SUMMARY_ID,
            name: ScenarioHelper.SCENARIO_SUMMARY_NAME,
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
                return acc.concat(...cur.messages);
            }, [])
        }

        /**
         * When there is only one scenario report, the summary report is set as the only one report.
         */
        return shouldScenarioSummaryReportIncluded ? [scenarioSummaryReport, ...reports] : reports;
    }

    /**
     * Converts the severity to a tag.
     * 
     * @param severity The severity of the report message.
     * @returns A tag for displaying the severity on the table.
     */
    static toSeverityTag(severity: ReportMessageSeverity): Tag {
        switch (severity) {
            case ReportMessageSeverity.INFO:
                return {
                    text: severity,
                    backgroundCssClass: 'bg-light-purple',
                    textCssClass: 'text-purple',
                };
            case ReportMessageSeverity.ERROR:
            case ReportMessageSeverity.ABORT:
                return {
                    text: severity,
                    textCssClass: 'text-pink',
                    backgroundCssClass: 'bg-light-pink'
                };
            case ReportMessageSeverity.WARN:
                return {
                    text: severity,
                    textCssClass: 'text-yellow',
                    backgroundCssClass: 'bg-light-yellow'
                };
            default:
                return {
                    text: severity,
                    textCssClass: 'text-grey',
                    backgroundCssClass: 'bg-grey-4'
                };
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
    static toTimeDisplayText(start: string, end: string): string {
        const startDate = new Date(start);
        const endDate = end ? new Date(end) : new Date();

        const intervalInMilliseconds = (endDate.getTime() - startDate.getTime());
        const intervalInHHMMSSFormat = TimeframeHelper.milliSecondsInHHMMSSFormat(intervalInMilliseconds);

        const hasIdenticalDate = startDate.toDateString() === endDate.toDateString();

        try {
            const startDateText = format(startDate, 'MM/dd/yy, HH:mm:ss');
            const endDateFormat = hasIdenticalDate ? 'HH:mm:ss' : 'MM/dd/yy, HH:mm:ss';
            const endDateText = format(endDate, endDateFormat);

            return end ?
                `${startDateText} -> ${endDateText}(${intervalInHHMMSSFormat})` :
                `${startDateText}(${intervalInHHMMSSFormat})`
        } catch (error) {
            console.error(error)
            return ''
        }
    }

    /**
     * Calculate the minion data series for the stacked bar chart.
     * 
     * @param completedMinions the number of completed minions
     * @param startedMinions the number of started minions
     * @param scheduledMinions the number of scheduled minions
     * @returns the data series for the stacked bar chart
     */
    static toMinionBarChartSeries(completedMinions: number, startedMinions: number, scheduledMinions: number): ApexAxisChartSeries {
        // The bar chart length for the completed minions is the same as the number of completed minions
        const completedMinionsBarChartLength = completedMinions;

        // The bar chart length for the started minions equals to the number of started minions minus the number of completed minions.
        const startedMinionsBarChartLength = startedMinions - completedMinions;

        // The bar chart length for the scheduled minions equals to the number of scheduled minions minus the number of started minions 
        const scheduledMinionsBarChartLength = scheduledMinions - startedMinions;

        return [
            {
                name: 'Completed Minions',
                data: [{
                    x: 0,
                    y: completedMinionsBarChartLength,
                }]
            }, {
                name: 'Started Minions',
                data: [{
                    x: 0,
                    y: startedMinionsBarChartLength,
                }]
            }, {
                name: 'Scheduled Minions',
                data: [{
                    x: 0,
                    y: scheduledMinionsBarChartLength,
                }]
            }
        ];
    }
}