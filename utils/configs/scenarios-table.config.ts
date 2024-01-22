export class ScenariosTableConfig {
  static TABLE_COLUMNS = [
    {
      title: "Scenario",
      dataIndex: "name",
      key: "name",
      sorter: (next: ScenarioSummary, prev: ScenarioSummary) =>
        next.name.localeCompare(prev.name),
    },
    {
      title: "Version",
      dataIndex: "version",
      key: "version",
      sorter: (next: ScenarioSummary, prev: ScenarioSummary) =>
        next.version.localeCompare(prev.name),
    },
    {
      title: "Description",
      dataIndex: "description",
      key: "description",
      sorter: (next: ScenarioSummary, prev: ScenarioSummary) => {
        const nextDescription = next.description ?? "";
        const prevDescription = prev.description ?? "";
        return nextDescription.localeCompare(prevDescription);
      },
    },
    {
      title: "",
      dataIndex: "actions",
      key: "actions",
    },
  ];
}
