import { ApexOptions } from "apexcharts";
import { eachDayOfInterval, format, isSameDay, sub } from "date-fns";
import { DateTime } from "luxon";
import tinycolor from "tinycolor2";

export class CampaignHelper {
  static TOOLTIP_RENDERER: (options: any) => any = ({
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
              </div>`
            );
          }
        })
    );

    return `<div class="custom-tooltip">
          <div class="custom-tooltip__title">
            <div class="custom-tooltip__title-day">${day}</div>
            <div class="custom-tooltip__title-time">${time}</div>
          </div>
          ${seriesContent.join("")}
        </div>`;
  };

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
      scheduled: campaignConfig.scheduling ? true : false,
      repeatEnabled: campaignConfig.scheduling?.scheduling ? true : false,
      repeatTimeRange: campaignConfig.scheduling?.scheduling ?? "DAILY",
      repeatValues: campaignConfig.scheduling?.restrictions
        ? campaignConfig.scheduling?.restrictions.map((r) => r.toString())
        : [],
      relativeRepeatValues:
        campaignConfig.scheduling?.scheduling === "MONTHLY" &&
        campaignConfig.scheduling?.restrictions
          ? campaignConfig.scheduling?.restrictions.filter(
              (restriction) => restriction < 0
            ).map(r => r.toString())
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

  static toCampaignSummaryChartData(campaignSummary: CampaignSummaryResult[]): ChartData {
    const xAxisEndDate = new Date();
    const xAxisStartDate = sub(new Date(), { days: 7 });
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
    const daysOfWeek = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    const xAxisCategories = xAxisDayIntervals.map(date => daysOfWeek[date.getDay()]);
      
    const chartOptions: ApexOptions = {
      chart: {
        type: "bar",
        stacked: true,
        stackType: "100%",
        toolbar: {
          show: false,
        }
      },
      colors: [ColorHelper.PRIMARY_COLOR_HEX_CODE, ColorHelper.GREY_2_HEX_CODE],
      plotOptions: {
        bar: {
          horizontal: false,
          borderRadius: 4
        }
      },
      xaxis: {
        categories: xAxisCategories,
      },
      yaxis: {
        show: false
      },
      dataLabels: {
        enabled: false
      },
      legend: {
        show: false
      },
      grid: {
        show: false
      },
      states: {
        hover: {
            filter: {
                type: 'none',
            }
        },
      }
    };
    const successfulDataSeries: number[] = [];
    const failedDataSeries: number[] = [];

    if (campaignSummary?.length > 0) {
      // Prepares the successful and failed data series
      xAxisDayIntervals.forEach(day => {
        const daySummary = campaignSummary.find(summary => isSameDay(new Date(summary.start), day));
        successfulDataSeries.push(daySummary?.successful ?? 0);
        failedDataSeries.push(daySummary?.failed ?? 0);
      })
    }

    return {
      chartOptions: chartOptions,
      chartDataSeries: [
        {
          name: 'successful',
          data: successfulDataSeries
        },
        {
          name: 'failed',
          data: failedDataSeries
        },
      ],
    }
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
    const chartOptions: ApexOptions = ChartHelper.DEFAULT_CHART_OPTIONS;
    const yAxisConfigs: ApexYAxis[] = [];
    chartOptions.tooltip!.custom = CampaignHelper.TOOLTIP_RENDERER;

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
            : `${ColorHelper.BLACK_HEX_CODE}`,
        isDurationNanoField:
          seriesDefinition?.fieldName === SeriesHelper.DURATION_NANO_FIELD_NAME,
        isMinionsCountSeries:
          seriesDefinition?.reference ===
          SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE,
        decimal:
          seriesDefinition?.reference ===
          SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE
            ? 0
            : seriesDefinition?.reference ===
              SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE
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
      elapsedTime: TimeframeHelper.elapsedTime(
        new Date(campaign.creation),
        campaign.end ? new Date(campaign.end) : new Date()
      ),
      statusTag: campaign.status
        ? CampaignHelper.toExecutionStatusTag(campaign.status)
        : null,
    }));
  }

  static toExecutionStatusTag(executionStatus: ExecutionStatus): Tag {
    switch (executionStatus) {
      case ExecutionStatusConstant.SUCCESSFUL:
        return {
          text: "Successful",
          textCssClass: "text-green",
          backgroundCssClass: "bg-light-green",
        };
      case ExecutionStatusConstant.FAILED:
        return {
          text: "Failed",
          textCssClass: "text-pink",
          backgroundCssClass: "bg-light-pink",
        };
      case ExecutionStatusConstant.IN_PROGRESS:
        return {
          text: "In progress",
          textCssClass: "text-purple",
          backgroundCssClass: "bg-light-purple",
        };
      case ExecutionStatusConstant.SCHEDULED:
        return {
          text: "Scheduled",
          textCssClass: "text-green",
          backgroundCssClass: "bg-grey-4",
        };
      case ExecutionStatusConstant.WARNING:
        return {
          text: "Warning",
          textCssClass: "text-yellow",
          backgroundCssClass: "bg-yellow",
        };
      case ExecutionStatusConstant.ABORTED:
        return {
          text: "Aborted",
          textCssClass: "text-pink",
          backgroundCssClass: "bg-light-pink",
        };
      case ExecutionStatusConstant.QUEUED:
        return {
          text: "Queued",
          textCssClass: "text-purple",
          backgroundCssClass: "bg-light-purple",
        };
      default:
        return {
          text: executionStatus,
          textCssClass: "text-grey",
          backgroundCssClass: "bg-grey-4",
        };
    }
  }

  static getRelativeDayOfMonthOptions(): FormMenuOption[] {
    // Create array [-7, -6, ..., -1]
    return Array.from({ length: 7 }, (_, i) => i - 7).map((num) => ({
      label: num.toString(),
      value: num.toString(),
    }));
  }

  static getMonthlyRepeatOptions(): FormMenuOption[] {
    // Create array [1, 2, ..., 31]
    return Array.from({ length: 31 }, (_, i) => i + 1).map((num) => ({
      label: num.toString(),
      value: num.toString(),
    }));
  }

  static getDailyRepeatOptions(): FormMenuOption[] {
    const weekMap = {
      Mo: 0,
      Tu: 1,
      We: 2,
      Th: 3,
      Fr: 4,
      Sa: 5,
      Su: 6,
    };

    return Object.entries(weekMap).map(([key, value]) => ({
      label: key,
      value: value.toString(),
    }));
  }

  static getHourlyRepeatOptions(): FormMenuOption[] {
    // Create array [1, 2, ..., 24]
    return Array.from({ length: 24 }, (_, i) => i + 1).map((num) => ({
      label: num.toString(),
      value: num.toString(),
    }));
  }

  static getRepeatTimeRangeOptions(): FormMenuOption[] {
    return [
      {
        label: "Day",
        value: "HOURLY",
      },
      {
        label: "Week",
        value: "DAILY",
      },
      {
        label: "Month",
        value: "MONTHLY",
      },
    ];
  }

  static getCampaignTimeoutOptions(): FormMenuOption[] {
    return [
      {
        label: "Soft Timeout",
        value: TimeoutTypeConstant.SOFT,
      },
      {
        label: "Hard Timeout",
        value: TimeoutTypeConstant.HARD,
      },
    ];
  }

  static getTableColumnConfigs() {
    return [
      {
        title: "Campaign",
        dataIndex: "name",
        key: "name",
        sorter: (next: CampaignTableData, prev: CampaignTableData) =>
          next.name.localeCompare(prev.name),
      },
      {
        title: "Scenario",
        dataIndex: "scenarioText",
        key: "scenarioText",
      },
      {
        title: "Status",
        dataIndex: "result",
        key: "result",
        sorter: (next: CampaignTableData, prev: CampaignTableData) =>
          next.status!.localeCompare(prev.status!),
      },
      {
        title: "Created",
        dataIndex: "creation",
        key: "creation",
        sorter: (next: CampaignTableData, prev: CampaignTableData) =>
          next.creation.localeCompare(prev.creation),
      },
      {
        title: "Elapsed time",
        dataIndex: "elapsedTime",
        key: "elapsedTime",
      },
      {
        title: "",
        dataIndex: "actions",
        key: "actions",
      },
    ];
  }
}
