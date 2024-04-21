export class SeriesTableConfig {
  static TABLE_COLUMNS: TableColumnConfig[] = [
    {
      title: "Name",
      key: "displayName",
      sortingEnabled: true
    },
    {
      title: "Time-series type",
      key: "dataType",
      sortingEnabled: true
    },
    {
      title: "Field name",
      key: "fieldName",
    },
    {
      title: "Value",
      key: "valueName",
    },
    {
      title: "Shared",
      key: "sharingMode",
      sortingEnabled: true
    }
  ];
}
