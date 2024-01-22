import { Report } from "../types/report";

export class ReportsTableConfig {
  static TABLE_COLUMNS = [
    {
      title: "Name",
      dataIndex: "displayName",
      key: "displayName",
      sorter: (next: Report, prev: Report) =>
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
      sorter: (next: Report, prev: Report) =>
        next.creator.localeCompare(prev.creator),
    },
    {
      title: "",
      dataIndex: "actions",
      key: "actions",
    },
  ];
}
