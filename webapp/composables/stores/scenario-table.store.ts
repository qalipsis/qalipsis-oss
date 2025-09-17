interface ScenarioTableState {
    currentPageIndex: number,
    pageSize: number,
    totalElements: number,
    dataSource: ScenarioSummary[],
    selectedRows: ScenarioSummary[],
    allScenarios: ScenarioSummary[],
    selectedRowKeys: string[],
    query: string,
    scenarioConfig: { [key: string]: ScenarioConfigurationForm }
}

export const useScenarioTableStore = defineStore("ScenarioTable", {
    state: (): ScenarioTableState => {
        return {
            currentPageIndex: 0,
            pageSize: TableHelper.defaultPageSize,
            totalElements: 0,
            dataSource: [],
            selectedRows: [],
            selectedRowKeys: [],
            scenarioConfig: {},
            allScenarios: [],
            query: ''
        }
    },
    getters: {
        currentPageNumber: state => state.currentPageIndex + 1,
    },
    actions: {
        async refreshScenarios(): Promise<void> {
            const {fetchScenarios} = useScenarioApi();
            const scenarios = await fetchScenarios();
            const query = this.query
                ? SearchHelper.getSanitizedQuery(this.query)
                : '';
            const filteredScenarios = SearchHelper.performFuzzySearch(
                query,
                scenarios,
                ['name']
            ) ?? [];

            this.dataSource = filteredScenarios;
            this.totalElements = filteredScenarios.length;
        }
    }
});
