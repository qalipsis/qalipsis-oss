import { defineStore } from "pinia";
import { DefaultCampaignConfiguration } from "utils/configuration";
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
    defaultCampaignConfiguration: DefaultCampaignConfiguration | null
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
            scenarioConfig: {},
            defaultCampaignConfiguration: null,
        }
    },
    getters: {
        currentPageNumber: state => state.currentPageIndex + 1,
    }
});
