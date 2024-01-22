export class ChartHelper {
  static getDataSeries(
    chartOptionData: ChartOptionData,
    timeAggregationResults: TimeSeriesAggregationResult[]
  ): ApexDataSeries {
    return {
      name: chartOptionData.dataSeriesName,
      data: timeAggregationResults.map((v) => ({
        x: new Date(v.start).getTime(),
        y: chartOptionData.isDurationNanoField
          ? (v.value / 1_000_000).toFixed(6)
          : v.value.toFixed(chartOptionData.decimal),
      })),
      color: chartOptionData.dataSeriesColor,
    };
  }

  static getYAxisOptions(chartOptionData: ChartOptionData): ApexYAxis {
    return {
      seriesName: chartOptionData.dataSeriesName,
      opposite: !chartOptionData.isMinionsCountSeries, // Position the minions count AXIS on the left side.
      decimalsInFloat: chartOptionData.decimal,
      labels: {
        show: true,
        formatter: (val, _) => {
          // Format the y axis label
          return `${val.toFixed(chartOptionData.decimal)}${
            chartOptionData.isDurationNanoField ? " ms" : ""
          }`;
        },
        style: {
          colors: [chartOptionData.dataSeriesColor],
          cssClass: "apexcharts-yaxis-label",
        },
      },
      title: {
        text: chartOptionData.isMinionsCountSeries ? "Minions count" : "",
      },
    };
  }

  static getEmptyChartYAxisOptions(scheduledMinions?: number): ApexYAxis {
    const yAxisConfig: ApexYAxis = {
      min: 0,
      showForNullSeries: true,
      decimalsInFloat: 0,
      floating: false,
      axisBorder: {
        show: true,
        color: "#e0e0e0",
      },
      labels: {
        show: true,
        style: {
          colors: "#000",
        },
        formatter: (val: number, _: any) => val.toFixed(2),
      },
      title: {
        text: "Minions Count",
        style: {
          cssClass: "apexcharts-yaxis-title",
        },
      },
    };

    if (scheduledMinions) {
      yAxisConfig.max = scheduledMinions;
      if (scheduledMinions <= 10) {
        yAxisConfig.tickAmount = scheduledMinions;
      }
    }

    return yAxisConfig;
  }
}
