export class ReportDetailsConfig {
  static TABLE_COLUMNS: TableColumnConfig[] = [
    {
      title: "Series name",
      key: "seriesName",
    },
    {
      title: "Campaign name",
      key: "campaignName",
    },
    {
      title: "Start time",
      key: "startTimeText",
    },
    {
      title: "Elapsed",
      key: "elapsedText",
    },
    {
      title: "Value",
      key: "valueDisplayText",
    },
  ];
}
