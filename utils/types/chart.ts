import type { ApexOptions } from "apexcharts";

export interface ChartData {
    /**
     * The chart options for rendering the chart
     * 
     * @see ApexOptions
     */
    chartOptions: ApexOptions;
    
    /**
     * The data series for the chart
     * @see ApexAxisChartSeries
     */
    chartDataSeries: ApexAxisChartSeries;
}

export interface ChartOptionData {
    /**
     * Name of the data series
     */
    dataSeriesName: string;
    /**
     * Color of the data series
     */
    dataSeriesColor: string;

    /**
     * A flag to indicate if the field from the data series is duration nano field
     */
    isDurationNanoField: boolean;
    /**
     * A flag to indicate if the data series is minion count
     */
    isMinionsCountSeries: boolean;

    /**
     * The decimal for displaying the data
     */
    decimal: number;
}

/**
 * This is the type from the ApexAxisChartSeries
 * @see ApexAxisChartSeries
 */
export interface ApexDataSeries {
    name?: string
    type?: string
    color?: string
    group?: string
    data:
      | (number | null)[]
      | {
          x: any;
          y: any;
          fill?: ApexFill;
          fillColor?: string;
          strokeColor?: string;
          meta?: any;
          goals?: any;
          barHeightOffset?: number;
          columnWidthOffset?: number;
        }[]
      | [number, number | null][]
      | [number, (number | null)[]][]
      | number[][];
}