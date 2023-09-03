import { defineStore } from "pinia";

export const useReportDetailsStore = defineStore("ReportDetails", {
    state: () => {
        return {
            reportDetails: null,
            reportName: '',
            dataComponents: [],
            campaignOptions: [],
            scenarioNames: [],
            activeScenarioNames: [],
            campaignPatterns: [],
            campaignKeys: []
        }
    }
});
