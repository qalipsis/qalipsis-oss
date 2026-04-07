export const ScenariosTableConfig = {
  TABLE_COLUMNS: [
    {
      title: 'Scenario',
      key: 'name',
      sortingEnabled: true,
      width: '25%',
    },
    {
      title: 'Version',
      key: 'version',
      sortingEnabled: true,
      width: '10%',
    },
    {
      title: 'Description',
      key: 'description',
      sortingEnabled: true,
    },
  ] as TableColumnConfig[],
}
