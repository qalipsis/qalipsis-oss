export class CampaignsTableConfig {
  static TABLE_COLUMNS: TableColumnConfig[] = [
    {
      title: 'Campaign',
      key: 'name',
      sortingEnabled: true,
    },
    {
      title: 'Scenarios',
      key: 'scenarioText',
    },
    {
      title: 'Status',
      key: 'result',
      sortingEnabled: true,
    },
    {
      title: 'Started',
      key: 'startTime',
      sortingEnabled: true,
    },
    {
      title: 'Elapsed time',
      key: 'elapsedTime',
    },
  ]
}
