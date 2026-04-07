interface ScenarioTableState {
  pageSize: number
  dataSource: ScenarioSummary[]
  selectedRows: ScenarioSummary[]
  allScenarios: ScenarioSummary[]
  selectedRowKeys: string[]
  query: string
  rowSelectionEnabled: boolean
  scenarioConfig: { [key: string]: ScenarioConfigurationForm }
}

export const useScenarioTableStore = defineStore('ScenarioTable', {
  state: (): ScenarioTableState => {
    return {
      pageSize: TableHelper.defaultPageSize,
      dataSource: [],
      selectedRows: [],
      selectedRowKeys: [],
      scenarioConfig: {},
      allScenarios: [],
      query: '',
      rowSelectionEnabled: true,
    }
  },
  getters: {},
  actions: {
    async refreshScenarios(): Promise<void> {
      const { fetchScenarios } = useScenarioApi()
      const scenarios = await fetchScenarios()
      const query = this.query ? TableHelper.getSanitizedQuery(this.query) : ''
      const filteredScenarios = SearchHelper.performFuzzySearch(query, scenarios, ['name']) ?? []

      this.dataSource = filteredScenarios
    },
  },
})
