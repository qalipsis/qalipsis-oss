export const useCampaignExecution = (
  campaignConfiguration: Ref<DefaultCampaignConfiguration | undefined>,
  campaignKey?: Ref<string | undefined>
) => {
  const { createCampaign, scheduleCampaign, updateCampaignConfig } = useCampaignApi()
  const scenarioTableStore = useScenarioTableStore()
  const toastStore = useToastStore()

  const executeCampaign = async (name: string, campaignConfigForm: CampaignConfigurationForm) => {
    if (!campaignConfiguration.value) return

    const selectedScenarioConfigMap: { [key: string]: ScenarioConfigurationForm } = {}
    const defaultStage = campaignConfiguration.value.validation.stage

    scenarioTableStore.selectedRows.forEach((scenario) => {
      const existingConfig = scenarioTableStore.scenarioConfig[scenario.name]
      if (existingConfig) {
        selectedScenarioConfigMap[scenario.name] = {
          ...existingConfig,
          executionProfileStages: existingConfig.executionProfileStages.map(
            (executionProfileStage) => ({
              ...executionProfileStage,
              resolution: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minResolution),
            })
          ),
        }
      } else {
        selectedScenarioConfigMap[scenario.name] = {
          executionProfileStages: [
            {
              minionsCount: defaultStage.minMinionsCount,
              duration: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minDuration),
              rampUpDuration: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minStartDuration),
              resolution: TimeframeHelper.isoStringToTargetTimeframeUnit(defaultStage.minResolution),
            },
          ],
          zones: [],
        }
      }
    })

    const request = CampaignHelper.toCampaignConfiguration(name, campaignConfigForm, selectedScenarioConfigMap)

    try {
      if (campaignConfigForm.scheduled) {
        if (campaignKey?.value) {
          await updateCampaignConfig(campaignKey.value, request)
        } else {
          await scheduleCampaign(request)
        }
        navigateTo('/campaigns')
      } else {
        const newCampaign = await createCampaign(request)
        navigateTo(`/campaigns/${newCampaign.key}`)
      }
    } catch (error) {
      toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    }
  }

  return { executeCampaign }
}
