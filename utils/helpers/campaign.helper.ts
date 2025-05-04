import type { ApexOptions } from "apexcharts";
import { eachDayOfInterval, format, isSameDay, sub } from "date-fns";
import { DateTime } from "luxon";
import tinycolor from "tinycolor2";
import type { TagStyleClass } from "../types/common";

const defaultTagClass: TagStyleClass = {
  backgroundCssClass: "bg-gray-100 dark:bg-gray-700",
  textCssClass: "text-gray-700 dark:text-gray-100",
}

const tagClass: { [key in ExecutionStatus]: Tag } = {
  SUCCESSFUL: {
    text: "Successful",
    backgroundCssClass: "bg-green-100 dark:bg-green-800",
    textCssClass: "text-green-600 dark:text-green-100"
  },
  WARNING: {
    text: "Warning",
    backgroundCssClass: "bg-yellow-100 dark:bg-yellow-800",
    textCssClass: "text-yellow-600 dark:text-yellow-100"
  },
  FAILED: {
    text: "Failed",
    backgroundCssClass: "bg-red-100 dark:bg-red-800",
    textCssClass: "text-red-600 dark:text-red-100",
  },
  ABORTED: {
    text: "Aborted",
    backgroundCssClass: "bg-red-100 dark:bg-red-800",
    textCssClass: "text-red-600 dark:text-red-100",
  },
  SCHEDULED: {
    text: "Scheduled",
    backgroundCssClass: "bg-gray-100 dark:bg-gray-700",
    textCssClass: "text-green-600 dark:text-green-100"
  },
  QUEUED: {
    text: "Queued",
    backgroundCssClass: "bg-purple-100 dark:bg-purple-800",
    textCssClass: "text-purple-600 dark:text-purple-100",
  },
  IN_PROGRESS: {
    text: "In progress",
    backgroundCssClass: "bg-purple-100 dark:bg-purple-800",
    textCssClass: "text-purple-600 dark:text-purple-100",
  },
};

const renderCampaignStaticTooltip: (options: any) => any = ({
  series, seriesIndex, dataPointIndex, w
}): string => {
  const statisticContent: string[] = [];

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
  });

  return `
    <div class="px-4 py-2 min-w-36 rounded-md text-gray-950 dark:bg-gray-900 dark:text-white font-light border-none">
      <div class="w-full flex items-center pb-2 border-b border-solid border-gray-100 dark:border-gray-500">
         <span>${w.globals.labels[dataPointIndex]}</span>
      </div>
      <div class="w-full flex items-center my-2">
        ${statisticContent.join("")}
      </div>
    </div>
  `;
}

const renderCampaignDetailsChartTooltip: (options: any) => any = ({
  seriesIndex,
  dataPointIndex,
  w,
}): string => {
  const dt =
    w.config.series[seriesIndex].data[dataPointIndex]?.x ||
    w.globals.initialSeries.filter(
      (serie: any) => !!serie.data[dataPointIndex]?.x
    )[0].data[dataPointIndex].x;
  const day = format(new Date(dt), "yy-MM-dd");
  const time = format(new Date(dt), "HH:mm:ss");
  let seriesContent: string[] = [];
  w.globals.initialSeries.forEach(
    (series: { data: any[]; color: any; name: any }) =>
      series.data.forEach((point) => {
        if (point?.x === dt) {
          seriesContent.push(
            `<div class="w-full flex items-center">
              <div class="w-3 h-3 mr-2 rounded-full border-2 border-solid border-white" style="background-color:${series.color}"></div>
              <div class="flex flex-grow justify-between items-center py-2  font-normal text-xs text-gray-200">
                <div class="pr-1">
                  ${series.name}:
                </div>
                <div class="ml-2">
                  ${point.y}
                </div>
              </div>
            </div>`
          );
        }
      })
  );

  return `<div class="p-4 min-w-72 rounded-md bg-gray-900 text-white font-light">
        <div class="w-full flex justify-between items-center pb-3 border-b border-solid border-gray-50">
          <div class="text-sm font-normal">${day}</div>
          <div class="text-sm font-normal">${time}</div>
        </div>
        ${seriesContent.join("")}
      </div>`;
};

export class CampaignHelper {
  static toCampaignConfigForm(
    campaignConfig: CampaignConfiguration
  ): CampaignConfigurationForm {
    const formattedTimeoutValue: FormattedTimeframe = campaignConfig.timeout
      ? TimeframeHelper.toFormattedTimeframe(campaignConfig.timeout)
      : { value: null, unit: TimeframeUnitConstant.MS };

    return {
      timeoutType: campaignConfig.hardTimeout ? "hard" : "soft",
      durationValue: formattedTimeoutValue.value
        ? formattedTimeoutValue.value.toString()
        : "",
      durationUnit: formattedTimeoutValue.unit,
      scheduled: campaignConfig.scheduledAt ? true : false,
      repeatEnabled: campaignConfig.scheduling?.scheduling ? true : false,
      repeatTimeRange: campaignConfig.scheduling?.scheduling ?? "DAILY",
      repeatValues: campaignConfig.scheduling?.restrictions
        ? campaignConfig.scheduling?.restrictions.map((r) => r.toString())
        : [],
      relativeRepeatValues:
        campaignConfig.scheduling?.scheduling === "MONTHLY" &&
        campaignConfig.scheduling?.restrictions
          ? campaignConfig.scheduling?.restrictions
              .filter((restriction) => restriction < 0)
              .map((r) => r.toString())
          : [],
      timezone: campaignConfig.scheduling?.timeZone ?? "",
      scheduledTime: campaignConfig.scheduledAt
        ? DateTime.fromISO(campaignConfig.scheduledAt).toJSDate()
        : null,
    };
  }

  static toCampaignConfiguration(
    campaignName: string,
    campaignConfigForm: CampaignConfigurationForm,
    scenarioConfigFormMap: { [key: string]: ScenarioConfigurationForm }
  ): CampaignConfiguration {
    const scenarios = Object.keys(scenarioConfigFormMap).reduce<{
      [key: string]: ScenarioRequest;
    }>((acc, cur) => {
      acc[cur] = ScenarioHelper.toScenarioRequest(scenarioConfigFormMap[cur]);

      return acc;
    }, {});

    const campaignConfiguration: CampaignConfiguration = {
      name: campaignName,
      speedFactor: 1,
      startOffsetMs: 1000,
      hardTimeout: campaignConfigForm.timeoutType === TimeoutTypeConstant.HARD,
      scenarios: scenarios,
    };

    if (campaignConfigForm.durationValue) {
      campaignConfiguration.timeout = TimeframeHelper.toMilliseconds(
        +campaignConfigForm.durationValue,
        campaignConfigForm.durationUnit
      );
    }

    if (campaignConfigForm.scheduled) {
      campaignConfiguration.scheduledAt = DateTime.fromISO(
        campaignConfigForm.scheduledTime!.toISOString()
      )
        .setZone(campaignConfigForm.timezone, { keepLocalTime: true })
        .toJSDate()
        .toISOString();
    }

    if (campaignConfigForm.repeatEnabled) {
      const restrictions =
        campaignConfigForm.repeatTimeRange === "MONTHLY"
          ? [
              ...campaignConfigForm.repeatValues,
              ...campaignConfigForm.relativeRepeatValues,
            ]
          : [...campaignConfigForm.repeatValues];
      campaignConfiguration.scheduling = {
        scheduling: campaignConfigForm.repeatTimeRange,
        timeZone: campaignConfigForm.timezone,
        restrictions: [...restrictions].map((restriction) => +restriction),
      };
    }

    return campaignConfiguration;
  }

  static toCampaignSummaryChartData(
    campaignSummary: CampaignSummaryResult[]
  ): ChartData {
    const xAxisEndDate = new Date();
    const xAxisStartDate = sub(new Date(), { days: 7 });
    const xAxisDayIntervals = eachDayOfInterval({
      start: new Date(
        xAxisStartDate.getFullYear(),
        xAxisStartDate.getMonth(),
        xAxisStartDate.getDate()
      ),
      end: new Date(
        xAxisEndDate.getFullYear(),
        xAxisEndDate.getMonth(),
        xAxisEndDate.getDate()
      ),
    });
    /**
     * This list maps to the week day index from the Date().
     *
     * E.g.,
     * The current date is on Sunday:
     * The result from new Date().getDay() will be 0
     */
    const daysOfWeek = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    const xAxisCategories = xAxisDayIntervals.map(
      (date) => daysOfWeek[date.getDay()]
    );

    const chartOptions: ApexOptions = {
      chart: {
        type: "bar",
        stacked: true,
        stackType: "100%",
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
            cssClass: 'fill-gray-800 dark:fill-gray-100'
          }
        }
      },
      yaxis: {
        show: false,
      },
      tooltip: {
        custom: renderCampaignStaticTooltip
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
            type: "none",
          },
        },
      },
    };
    const successfulDataSeries: number[] = [];
    const failedDataSeries: number[] = [];

    if (campaignSummary?.length > 0) {
      // Prepares the successful and failed data series
      xAxisDayIntervals.forEach((day) => {
        const daySummary = campaignSummary.find((summary) =>
          isSameDay(new Date(summary.start), day)
        );
        successfulDataSeries.push(daySummary?.successful ?? 0);
        failedDataSeries.push(daySummary?.failed ?? 0);
      });
    }

    return {
      chartOptions: chartOptions,
      chartDataSeries: [
        {
          name: "successful",
          data: successfulDataSeries,
        },
        {
          name: "failed",
          data: failedDataSeries,
        },
      ],
    };
  }

  static toChartData(
    aggregationResult: { [key: string]: TimeSeriesAggregationResult[] },
    dataSeries: DataSeries[],
    campaignExecutionDetails: CampaignExecutionDetails
  ): ChartData {
    const aggregations = Object.entries(aggregationResult)?.filter(
      ([_, value]) => value.length
    );
    const chartDataSeries: ApexAxisChartSeries = [];
    const chartOptions: ApexOptions = ChartsConfig.DEFAULT_CHART_OPTIONS;
    const yAxisConfigs: ApexYAxis[] = [];
    chartOptions.tooltip!.custom = renderCampaignDetailsChartTooltip;

    // No aggregation result, returns an empty chart with the scheduled minions of the campaigns.
    if (!aggregations || aggregations?.length === 0) {
      chartOptions.yaxis = ChartHelper.getEmptyChartYAxisOptions(
        campaignExecutionDetails.scheduledMinions
      );

      return {
        chartOptions: chartOptions,
        chartDataSeries: [],
      };
    }

    // Prepares the data series for the chart
    aggregations.forEach(([key, value]) => {
      const seriesDefinition = dataSeries.find((s) => s.reference === key);
      const chartOptionData: ChartOptionData = {
        dataSeriesName: seriesDefinition?.displayName ?? key,
        dataSeriesColor:
          seriesDefinition?.color &&
          tinycolor(seriesDefinition?.color).isValid()
            ? seriesDefinition?.color
            : `${ColorsConfig.PURPLE_COLOR_HEX_CODE}`,
        isDurationNanoField:
          seriesDefinition?.fieldName === SeriesDetailsConfig.DURATION_NANO_FIELD_NAME,
        isMinionsCountSeries:
          key === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE,
        decimal:
          seriesDefinition?.reference ===
          SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
            ? 0
            : seriesDefinition?.reference ===
              SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE
            ? 6
            : 2,
      };

      // The data series for the chart.
      const series = ChartHelper.getDataSeries(chartOptionData, value);

      // The y axis config for the chart.
      const yAxisConfig: ApexYAxis =
        ChartHelper.getYAxisOptions(chartOptionData);
      chartDataSeries.push(series);
      yAxisConfigs.push(yAxisConfig);
    });

    chartOptions.yaxis = yAxisConfigs;

    return {
      chartOptions: chartOptions,
      chartDataSeries: chartDataSeries,
    };
  }

  static toTableData(campaigns: Campaign[]): CampaignTableData[] {
    return campaigns.map((campaign) => ({
      ...campaign,
      scenarioText: campaign.scenarios
        .map((scenario) => scenario.name)
        .join(","),
      creationTime: TimeframeHelper.toSpecificFormat(
        new Date(campaign.creation),
        "dd/MM/yyyy, HH:mm:ss"
      ),
      /**
       * Only shows the elapsed time when the status is scheduled,
       * or the status is aborted and there is no end time for the campaign.
       */
      elapsedTime:
        campaign.status === "SCHEDULED" || 
        (campaign.status === "ABORTED" && !campaign.end)
          ? "-"
          : TimeframeHelper.elapsedTime(
              new Date(campaign.creation),
              campaign.end ? new Date(campaign.end) : new Date()
            ),
      statusTag: campaign.status
        ? CampaignHelper.toExecutionStatusTag(campaign.status)
        : null,
    }));
  }

  static toExecutionStatusTag(executionStatus: ExecutionStatus): Tag {
    const tag: Tag = tagClass[executionStatus] ?? {
      text: executionStatus,
      backgroundCssClass: defaultTagClass.backgroundCssClass,
      textCssClass: defaultTagClass.textCssClass
    }

    return tag
  }
}
