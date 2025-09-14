
export class CampaignsTableConfig {
  static TABLE_COLUMNS: TableColumnConfig[] = [{
    title: "Campaign",
    key: "name",
    sortingEnabled: true
  }, {
    title: "Scenarios",
    key: "scenarioText"
  }, {
    title: "Status",
    key: "result",
    sortingEnabled: true
  }, {
    title: "Created",
    key: "creation",
    sortingEnabled: true
  }, {
    title: "Elapsed time",
    key: "elapsedTime"
  }]
}
