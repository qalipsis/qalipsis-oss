import { defineStore } from "pinia";
import { ScenarioConfigurationForm, ScenarioSummary } from "utils/scenario";

interface ScenarioTableState {
    currentPageIndex: number,
    pageSize: number,
    totalElements: number,
    allScenarioSummary: ScenarioSummary[],
    dataSource: ScenarioSummary[],
    selectedRows: ScenarioSummary[],
    selectedRowKeys: string[],
    scenarioConfig:  { [key: string]: ScenarioConfigurationForm }
}

export const useScenarioTableStore = defineStore("ScenarioTable", {
    state: (): ScenarioTableState => {
        return {
            currentPageIndex: 0,
            pageSize: TableHelper.defaultPageSize,
            totalElements: 0,
            allScenarioSummary: [],
            dataSource: [],
            selectedRows: [],
            selectedRowKeys: [],
            scenarioConfig: {}
        }
    },
    getters: {
        currentPageNumber: state => state.currentPageIndex + 1,
    }
});
