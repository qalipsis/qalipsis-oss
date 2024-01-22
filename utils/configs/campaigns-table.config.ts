export class CampaignsTableConfig {
  static TABLE_COLUMNS = [
    {
      title: "Campaign",
      dataIndex: "name",
      key: "name",
      sorter: (next: CampaignTableData, prev: CampaignTableData) =>
        next.name.localeCompare(prev.name),
    },
    {
      title: "Scenario",
      dataIndex: "scenarioText",
      key: "scenarioText",
    },
    {
      title: "Status",
      dataIndex: "result",
      key: "result",
      sorter: (next: CampaignTableData, prev: CampaignTableData) =>
        next.status!.localeCompare(prev.status!),
    },
    {
      title: "Created",
      dataIndex: "creation",
      key: "creation",
      sorter: (next: CampaignTableData, prev: CampaignTableData) =>
        next.creation.localeCompare(prev.creation),
    },
    {
      title: "Elapsed time",
      dataIndex: "elapsedTime",
      key: "elapsedTime",
    },
    {
      title: "",
      dataIndex: "actions",
      key: "actions",
    }
  ];
}
