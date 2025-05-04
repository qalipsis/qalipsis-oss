import type { ApexOptions } from "apexcharts";
import tinycolor from "tinycolor2";
import { format } from "date-fns";

const renderReportChartTooltip: ((options: any) => any) = ({ seriesIndex, dataPointIndex, w }): string => {
    const elapsedTime = w.config.series[seriesIndex].data[dataPointIndex]?.x;
    let seriesContent: string[] = []
    w.globals.initialSeries.forEach((series: { data: any[]; name: string; color: any; }) => series.data.forEach(point => {
        if (point?.x === elapsedTime) {
            const day = format(new Date(point.meta), 'yy-MM-dd');
            const time = format(new Date(point.meta), 'HH:mm:ss');
            const campaignName = series.name.substring(
                series.name.indexOf('{') + 1, 
                series.name.lastIndexOf('}')
            );
            const seriesName = series.name.replace(`{${campaignName}}`, '');
            seriesContent.push(
                `<div class="w-full flex items-center">
            <div class="w-3 h-3 pr-2 rounded-full border-2 border-solid border-white" style="background-color:${series.color}"></div>
            <div class="flex flex-grow justify-between items-center py-2 text-xs font-normal text-gray-500">
              <div>
                <div>
                    ${day}, ${time}:
                </div>
                <div class="pr-1">
                    ${campaignName} - ${seriesName}
                </div>
              </div>
              <div class="ml-2">
                ${point.y}
              </div>
            </div>
          </div>`)
        }
    }));

    return `<div class="p-4 min-w-72 rounded-xl bg-gray-900 text-white font-light">
      <div class="w-full flex justify-between items-center pb-3 border-b border-solid border-gray-50">
        <span>Elapsed time: ${elapsedTime} s</span>
      </div>
      ${seriesContent.join('')}
    </div>`
}

export class ReportHelper {
    static toReportTableData(reports: DataReport[]): ReportTableData[] {
        return reports.map(report => ({
            ...report,
            description: report.description ?? '-',
            creator: report.creator ?? '-',
            concatenatedCampaignNames: report.resolvedCampaigns?.map(campaign => campaign.name)?.join(',')
        }))
    }
    
    static toReportChartData(timeSeriesAggregationResult: { [seriesReference: string]: TimeSeriesAggregationResult[] }, 
        dataSeries: DataSeries[], campaignOptions: CampaignOption[]): ChartData {
        const chartDataSeriesStrokeArrayList: number[] = [];
        const campaignKeyToOption: { [key: string]: CampaignOption } = campaignOptions.reduce<{ [key: string]: CampaignOption }>((acc, cur) => {
            acc[cur.key] = cur;

            return acc
        }, {})
        const chartOptions: ApexOptions = ChartsConfig.DEFAULT_CHART_OPTIONS;
        const aggregations = Object.entries(timeSeriesAggregationResult)?.filter(([_, value]) => value.length);
        const chartDataSeries: ApexAxisChartSeries = [];
        const yAxisConfigs: ApexYAxis[] = [];

        // No aggregation result, returns an empty chart with the scheduled minions of the campaigns.
        if (!aggregations || aggregations?.length === 0) {
            // Finds the maximum schedule minions from the campaign details.
            const scheduledMinions = campaignOptions
                .map(campaignOption => campaignOption.scheduledMinions)
                .filter(scheduledMinions => scheduledMinions && scheduledMinions >= 0)
                .reduce((acc, cur) => {
                    if (cur! > acc!) {
                        acc = cur;
                    }

                    return acc;
                }, 0);
            chartOptions.yaxis = ChartHelper.getEmptyChartYAxisOptions(scheduledMinions);

            return {
                chartOptions: chartOptions,
                chartDataSeries: []
            }
        }

        // Sets the x-axis config type to be numeric
        chartOptions.xaxis!.type = 'numeric';
        chartOptions.xaxis!.tooltip!.enabled = false;

        // Prepare the data series
        aggregations.forEach(([key, values]) => {
            const seriesDefinition = dataSeries.find(s => s.reference === key);
            const chartOptionData: ChartOptionData = {
                dataSeriesName: seriesDefinition?.displayName ?? key,
                dataSeriesColor: seriesDefinition?.color && tinycolor(seriesDefinition?.color).isValid() ? seriesDefinition?.color : `${ColorsConfig.PURPLE_COLOR_HEX_CODE}`,
                isDurationNanoField: seriesDefinition?.fieldName === SeriesDetailsConfig.DURATION_NANO_FIELD_NAME,
                isMinionsCountSeries: key === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE,
                decimal: seriesDefinition?.reference === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
                    ? 0 : (seriesDefinition?.reference === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE ? 6 : 2)
            }
            
            const campaignChartSeriesMap: { [key: string]: TimeSeriesAggregationResult[]} = values.reduce<{[key: string]: TimeSeriesAggregationResult[]}>((acc, cur) => {
                if (acc[cur.campaign]) {
                    acc[cur.campaign].push(cur);
                } else {
                    acc[cur.campaign] = [cur]
                }
                return acc;
            }, {});

            Object.entries(campaignChartSeriesMap).forEach(([campaignKey, campaignSeriesValues]: [string, TimeSeriesAggregationResult[]]) => {
                // The data series for the chart.
                const campaignName = campaignKeyToOption[campaignKey].name;
                const lineChartDataSeries = {
                    name: `${chartOptionData.dataSeriesName}{${campaignName}}`,
                    data: campaignSeriesValues.map(v => ({
                        meta: new Date(v.start).getTime(),
                        x: v.elapsed,
                        y: chartOptionData.isDurationNanoField ? (v.value / 1_000_000).toFixed(6) : (v.value).toFixed(chartOptionData.decimal)
                    })),
                    color: chartOptionData.dataSeriesColor
                };
                chartDataSeries.push(lineChartDataSeries);
            });

            const strokeArray = Object.keys(campaignChartSeriesMap).map(campaignKey => {
                return campaignKeyToOption[campaignKey].strokeDashArray;
            })
            chartDataSeriesStrokeArrayList.push(...strokeArray);

            // The y axis config for the chart.
            const yAxisConfig: ApexYAxis = ChartHelper.getYAxisOptions(chartOptionData);
            
            yAxisConfigs.push(yAxisConfig);
        });

        chartOptions.yaxis = yAxisConfigs;
        chartOptions.tooltip!.custom = renderReportChartTooltip;
        chartOptions.stroke!.dashArray = chartDataSeriesStrokeArrayList;

        return {
            chartOptions: chartOptions,
            chartDataSeries: chartDataSeries
        }
    }

    static toReportDetailsTableData(timeSeriesAggregationResult: { [seriesReference: string]: TimeSeriesAggregationResult[] }, 
        dataSeries: DataSeries[], campaigns: CampaignOption[]): ReportDetailsTableData[] {
        const dataSeriesReferenceToDataSeries: { [key: string]: DataSeries } = dataSeries.reduce<{ [key: string]: DataSeries }>((acc, cur) => {
            acc[cur.reference] = cur;
            return acc;
        }, {});
        const campaignKeyToName: { [key: string]: string } = campaigns.reduce<{ [key: string]: string }>((acc, cur) => {
            acc[cur.key] = cur.name;
            return acc;
        }, {});
    
        return Object.keys(timeSeriesAggregationResult)
            .reduce<ReportDetailsTableData[]>((acc, cur) => {
                const reportTableDataComponents: ReportDetailsTableData[] = timeSeriesAggregationResult[cur]
                    .map<ReportDetailsTableData>(res => {
                        const formattedAggregatedValue = TimeSeriesHelper.toComposedValue(dataSeriesReferenceToDataSeries[cur], res.value);
                        const reportTableData: ReportDetailsTableData = {
                            id: `${res.campaign}_${cur}`,
                            seriesReference: cur,
                            seriesName: dataSeriesReferenceToDataSeries[cur].displayName,
                            campaignKey: res.campaign,
                            campaignName: campaignKeyToName[res.campaign],
                            startTime: res.start,
                            elapsed: res.elapsed,
                            elapsedText: `${res.elapsed} s`,
                            value: res.value,
                            startTimeText: TimeframeHelper.toSpecificFormat(new Date(res.start), 'dd/MM/yyyy, HH:mm:ss'),
                            valueDisplayText: formattedAggregatedValue.formattedText
                        }

                        return reportTableData
                    })
                acc.push(...reportTableDataComponents);

                return acc
            }, []);
    }
}