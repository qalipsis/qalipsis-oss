import type { ColumnType } from "ant-design-vue/es/table/interface";

export class ReportsTableConfig {
  static TABLE_COLUMNS: ColumnType<any>[] = [
    {
      title: "Name",
      dataIndex: "displayName",
      key: "displayName",
      sorter: (next: DataReport, prev: DataReport) =>
        next.displayName.localeCompare(prev.displayName),
    },
    {
      title: "Campaigns",
      dataIndex: "concatenatedCampaignNames",
      key: "concatenatedCampaignNames",
    },
    {
      title: "Description",
      dataIndex: "description",
      key: "description",
      sorter: (next: DataReport, prev: DataReport) =>
        next.description ? next.description.localeCompare(prev.description ?? '') : 0,
    },
    {
      title: "",
      dataIndex: "actions",
      key: "actions",
    },
  ];
}
