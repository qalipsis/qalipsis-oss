interface ReportDetailStoreState {
  reportDetails: DataReport | null
  reportName: string
  dataComponents: DataComponent[]
  campaignOptions: CampaignOption[]
  scenarioNames: string[]
  selectedScenarioNames: string[]
  campaignNamesPatterns: string[]
  description: string
  campaignKeys: string[]
  allDataSeriesOptions: DataSeriesOption[]
}

export const useReportDetailsStore = defineStore('ReportDetails', {
  state: (): ReportDetailStoreState => {
    return {
      reportDetails: null,
      reportName: '',
      description: '',
      dataComponents: [],
      campaignOptions: [],
      scenarioNames: [],
      selectedScenarioNames: [],
      campaignNamesPatterns: [],
      campaignKeys: [],
      allDataSeriesOptions: [],
    }
  },
  getters: {
    activeCampaignOptions: (state) => state.campaignOptions.filter((c: CampaignOption) => c.isActive),
    hasUnsavedChanges: (state) => {
      const simplifiedNewDataComponents = state.dataComponents
        ? state.dataComponents.map((d: DataComponent) => ({
            dataType: d.type,
            references: d.datas.map((data: DataSeries) => data.reference),
          }))
        : []

      const simplifiedOldDataComponents = state.reportDetails?.dataComponents
        ? state.reportDetails.dataComponents.map((d: DataComponent) => ({
            dataType: d.type,
            references: d.datas.map((data: DataSeries) => data.reference),
          }))
        : []

      if (state.reportName !== state.reportDetails?.displayName) return true
      else if (!arraysEqual(simplifiedNewDataComponents, simplifiedOldDataComponents)) return true

      return false
    },
  },
  actions: {
    deleteDataComponent(dataIndex: number): void {
      this.dataComponents.splice(dataIndex, 1)
    },
  },
})
