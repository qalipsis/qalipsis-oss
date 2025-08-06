<template>
    <div v-if="campaignConfigForm && campaignConfiguration">
        <CampaignConfigHeader 
            :campaign-configuration="campaignConfiguration" 
            :campaign-config-form="campaignConfigForm"
            :campaign-name="campaignName"
            :campaign-key="campaignKey"
        />
        <CampaignConfigContent 
            :campaign-configuration="campaignConfiguration"
        />
    </div>
</template>

<script setup lang="ts">

const { fetchCampaignConfig } = useCampaignApi();
const { fetchCampaignConfiguration } = useConfigurationApi()
const scenarioTableStore = useScenarioTableStore();
const route = useRoute();

const campaignConfiguration = ref<DefaultCampaignConfiguration>()

const campaignConfigForm = ref<CampaignConfigurationForm>();
const campaignName = ref<string>();
const campaignKey = ref<string>();

onMounted(async () => {
    try {
        campaignKey.value = route.params.id as string;
        // Prepares the default campaign configuration for configuring.
        campaignConfiguration.value = await fetchCampaignConfiguration()
        const campaignConfig = await fetchCampaignConfig(campaignKey.value);
        campaignName.value = campaignConfig.name;
        campaignConfigForm.value = CampaignHelper.toCampaignConfigForm(campaignConfig);
        const selectedScenarios: Scenario[] = Object.entries(campaignConfig.scenarios).map(([key, value]) => ({
            name: key,
            minionsCount: value.minionsCount,
            version: ""
        }));
        const selectedScenarioKeys = Object.keys(campaignConfig.scenarios);
        const scenarioKeyToScenarioForm = ScenarioHelper.toScenarioConfigForm(campaignConfig);
        scenarioTableStore.$patch({
            selectedRowKeys: selectedScenarioKeys,
            selectedRows: selectedScenarios,
            scenarioConfig: scenarioKeyToScenarioForm
        });
    } catch (error) {
        ErrorHelper.getErrorMessage(error)
    }

})
</script>
