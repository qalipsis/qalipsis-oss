import { ApexOptions } from "apexcharts";

export class ScenarioDetailsConfig {
  /**
   * The name for the scenario summary.
   */
  static SCENARIO_SUMMARY_NAME = "Campaign Summary";

  /**
   * The identifier for the scenario summary.
   */
  static SCENARIO_SUMMARY_ID = "campaignSummary";

  static MESSAGE_TABLE_COLUMNS = [
    {
      title: "Step Name",
      dataIndex: "stepName",
      key: "displayName",
    },
    {
      title: "Severity",
      dataIndex: "severity",
      key: "severity",
    },
    {
      title: "Message",
      dataIndex: "message",
      key: "message",
    },
  ];

  static CHART_OPTIONS: ApexOptions = {
    chart: {
      type: "area",
      zoom: {
        enabled: false,
      },
      offsetX: -25,
    },
    dataLabels: {
      enabled: false,
    },
    stroke: {
      curve: "straight",
      width: 0.5,
    },
    grid: {
      row: {
        colors: ["#fff", "transparent"],
        opacity: 0.5,
      },
    },
    xaxis: {
      decimalsInFloat: 0,
      type: "numeric",
      tickAmount: "dataPoints",
      title: {
        text: "Duration, s",
        offsetX: 260,
        offsetY: -5,
        style: {
          color: "#ddd",
          fontSize: "12px",
          fontWeight: 400,
        },
      },
    },
    yaxis: {
      decimalsInFloat: 0,
      tickAmount: 2,
      min: 0,
      max: 10,
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
    },
  };

  static MINION_STACKED_BAR_CHART_OPTIONS: ApexOptions = {
    colors: [
      ColorsConfig.PRIMARY_COLOR_HEX_CODE,
      ColorsConfig.PURPLE_COLOR_HEX_CODE,
      ColorsConfig.GREY_2_HEX_CODE,
    ],
    chart: {
      type: "bar",
      stacked: true,
      stackType: "100%",
      toolbar: {
        show: false,
      },
      zoom: {
        enabled: false,
      },
    },
    grid: {
      show: false,
      xaxis: {
        lines: {
          show: false,
        },
      },
      yaxis: {
        lines: {
          show: false,
        },
      },
      // Note: this is a workaround to remove unneeded paddings from the apex chart
      padding: {
        top: -18,
        right: 0,
        bottom: -72,
        left: -12,
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
        show: false,
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
        show: false,
      },
    },
    legend: {
      show: false,
    },
    fill: {
      opacity: 1,
    },
    dataLabels: {
      enabled: false,
    },
    tooltip: {
      enabled: false,
    },
    states: {
      hover: {
        filter: {
          type: "none",
        },
      },
      active: {
        filter: {
          type: "none",
        },
      },
    },
  };

  static EXECUTION_STEP_DONUT_CHART_OPTIONS: ApexOptions = {
    colors: [ColorsConfig.PRIMARY_COLOR_HEX_CODE, ColorsConfig.PINK_HEX_CODE],
    chart: {
      type: "donut",
    },
    stroke: {
      width: 0,
    },
    plotOptions: {
      pie: {
        expandOnClick: false,
        donut: {
          size: "65%",
        },
      },
    },
    grid: {
      // Note: this is a workaround to remove unneeded paddings from the apex chart
      padding: {
        top: -2,
        right: -2,
        bottom: -8,
        left: -2,
      },
    },
    legend: {
      show: false,
    },
    fill: {
      opacity: 1,
    },
    dataLabels: {
      enabled: false,
    },
    tooltip: {
      enabled: false,
    },
    states: {
      hover: {
        filter: {
          type: "none",
        },
      },
      active: {
        filter: {
          type: "none",
        },
      },
    },
  };
}
