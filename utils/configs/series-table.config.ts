export class SeriesTableConfig {
  static TABLE_COLUMNS = [
    {
      title: "Name",
      dataIndex: "displayName",
      key: "displayName",
      sorter: (next: DataSeriesTableData, prev: DataSeriesTableData) =>
        next.displayName.localeCompare(prev.displayName),
    },
    {
      title: "Time-series type",
      dataIndex: "dataType",
      key: "dataType",
      sorter: (next: DataSeriesTableData, prev: DataSeriesTableData) =>
        next.dataType.localeCompare(prev.dataType),
    },
    {
      title: "Field name",
      dataIndex: "fieldName",
      key: "fieldName",
    },
    {
      title: "Value",
      dataIndex: "valueName",
      key: "valueName",
    },
    {
      title: "Shared",
      dataIndex: "sharingMode",
      key: "sharingMode",
      sorter: (next: DataSeriesTableData, prev: DataSeriesTableData) => {
        const nextSharingMode = next?.sharingMode ?? "";
        const prevSharingMode = prev?.sharingMode ?? "";

        return nextSharingMode.localeCompare(prevSharingMode);
      },
    },
    {
      title: "",
      dataIndex: "actions",
      key: "actions",
    },
  ];
}
