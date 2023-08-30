import { format } from "date-fns";
import tinycolor from "tinycolor2";

export class CampaignHelper {
    static TOOLTIP_RENDERER: ((options: any) => any) = ({ seriesIndex, dataPointIndex, w }): string => {
        const dt = w.config.series[seriesIndex].data[dataPointIndex]?.x ||
            w.globals.initialSeries.filter(serie => !!serie.data[dataPointIndex]?.x)[0].data[dataPointIndex].x;
        const day = format(new Date(dt), 'yy-MM-dd');
        const time = format(new Date(dt), 'HH:mm:ss');
        let seriesContent: string[] = []
        w.globals.initialSeries.forEach(series => series.data.forEach(point => {
            if (point?.x === dt) {
                seriesContent.push(
                    `<div class="custom-tooltip__content">
                <div class="custom-tooltip__marker" style="background-color:${series.color}"></div>
                <div class="custom-tooltip__text">
                  <div class="custom-tooltip__text-name">
                    ${series.name}:
                  </div>
                  <div class="custom-tooltip__text-y">
                    ${point.y}
                  </div>
                </div>
              </div>`)
            }
        }));
    
        return `<div class="custom-tooltip">
          <div class="custom-tooltip__title">
            <div class="custom-tooltip__title-day">${day}</div>
            <div class="custom-tooltip__title-time">${time}</div>
          </div>
          ${seriesContent.join('')}
        </div>`
    }
    
    static toChartData(aggregationResult: { [key: string]: TimeSeriesAggregationResult[] }, dataSeries: DataSeries[], campaignExecutionDetails: CampaignExecutionDetails): ChartData {
        const aggregations = Object.entries(aggregationResult)?.filter(([_, value]) => value.length);
        const chartDataSeries: ApexAxisChartSeries = [];
        const chartOptions = ChartHelper.DEFAULT_CHART_OPTIONS;
        const yAxisConfigs: ApexYAxis[] = [];
        chartOptions.tooltip!.custom = CampaignHelper.TOOLTIP_RENDERER;
        
        // No aggregation result, returns an empty chart with the scheduled minions of the campaigns.
        if (!aggregations || aggregations?.length === 0) {
            chartOptions.yaxis = ChartHelper.getEmptyChartYAxisOptions(campaignExecutionDetails.scheduledMinions);

            return {
                chartOptions: chartOptions,
                chartDataSeries: []
            }
        }

        // Prepares the data series for the chart
        aggregations.forEach(([key, value]) => {
            const seriesDefinition = dataSeries.find(s => s.reference === key);
            const chartOptionData: ChartOptionData = {
                dataSeriesName: seriesDefinition?.displayName ?? key,
                dataSeriesColor: seriesDefinition?.color && tinycolor(seriesDefinition?.color).isValid() ? seriesDefinition?.color : `${ColorHelper.BLACK_HEX_CODE}`,
                isDurationNanoField: seriesDefinition?.fieldName === SeriesHelper.DURATION_NANO_FIELD_NAME,
                isMinionsCountSeries: seriesDefinition?.reference === SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE,
                decimal: seriesDefinition?.reference === SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE
                    ? 0 : (seriesDefinition?.reference === SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE ? 6 : 2)
            }
      
            // The data series for the chart.
            const series = ChartHelper.getDataSeries(chartOptionData, value);
      
            // The y axis config for the chart.
            const yAxisConfig: ApexYAxis = ChartHelper.getYAxisOptions(chartOptionData);
            chartDataSeries.push(series);
            yAxisConfigs.push(yAxisConfig);
        });

        chartOptions.yaxis = yAxisConfigs

        return {
            chartOptions: chartOptions,
            chartDataSeries: chartDataSeries
        }
    }

    static toTableData(campaigns: Campaign[]): CampaignTableData[] {
        return campaigns.map(campaign => ({
            ...campaign,
            scenarioText: campaign.scenarios.map(scenario => scenario.name).join(','),
            creationTime: TimeframeHelper.toSpecificFormat(new Date(campaign.creation), 'dd/MM/yyyy, HH:mm:ss'),
            elapsedTime: TimeframeHelper.elapsedTime(new Date(campaign.creation), campaign.end ? new Date(campaign.end) : new Date()),
            statusTag: CampaignHelper.toExecutionStatusTag(campaign.status)
        }))
    }

    static toExecutionStatusTag(executionStatus: ExecutionStatus): Tag {
        switch (executionStatus) {
            case ExecutionStatus.SUCCESSFUL:
                return {
                    text: 'Successful',
                    textCssClass: 'text-green',
                    backgroundCssClass: 'bg-light-green'
                };
            case ExecutionStatus.FAILED:
                return {
                    text: 'Failed',
                    textCssClass: 'text-pink',
                    backgroundCssClass: 'bg-light-pink'
                };
            case ExecutionStatus.IN_PROGRESS:
                return {
                    text: 'In progress',
                    textCssClass: 'text-purple',
                    backgroundCssClass: 'bg-light-purple'
                };
            case ExecutionStatus.SCHEDULED:
                return {
                    text: 'Scheduled',
                    textCssClass: 'text-green',
                    backgroundCssClass: 'bg-grey-4'
                };
            case ExecutionStatus.WARNING:
                return {
                    text: 'Warning',
                    textCssClass: 'text-yellow',
                    backgroundCssClass: 'bg-yellow'
                };
            case ExecutionStatus.ABORTED:
                return {
                    text: 'Aborted',
                    textCssClass: 'text-pink',
                    backgroundCssClass: 'bg-light-pink'
                };
            case ExecutionStatus.QUEUED:
                return {
                    text: 'Queued',
                    textCssClass: 'text-purple',
                    backgroundCssClass: 'bg-light-purple'
                };
            default:
                return {
                    text: executionStatus,
                    textCssClass: 'text-grey',
                    backgroundCssClass: 'bg-grey-4'
                };
        }
    }

    static getTableColumnConfigs() {
        return [
            {
                title: 'Campaign',
                dataIndex: 'name',
                key: 'name',
                sorter: (next: CampaignTableData, prev: CampaignTableData) => next.name.localeCompare(prev.name),
            },
            {
                title: 'Scenario',
                dataIndex: 'scenarioText',
                key: 'scenarioText',
            },
            {
                title: 'Status',
                dataIndex: 'result',
                key: 'result',
                sorter: (next: CampaignTableData, prev: CampaignTableData) => next.status.localeCompare(prev.status),
            },
            {
                title: 'Created',
                dataIndex: 'creation',
                key: 'creation',
                sorter: (next: CampaignTableData, prev: CampaignTableData) => next.creation.localeCompare(prev.creation),
            },
            {
                title: 'Elapsed time',
                dataIndex: 'elapsedTime',
                key: 'elapsedTime'
            }
        ];
    }
}