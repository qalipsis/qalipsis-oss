export class ReportsTableConfig {
  static TABLE_COLUMNS: TableColumnConfig[] = [
    {
      title: 'Name',
      key: 'displayName',
      sortingEnabled: true,
    },
    {
      title: 'Description',
      key: 'description',
      sortingEnabled: true,
    },
    {
      title: 'Campaigns',
      key: 'concatenatedCampaignNames',
    },
  ]
}
