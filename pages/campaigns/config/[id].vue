<template>
    <div v-if="campaignConfigForm">
        <CampaignConfigHeader 
            :campaign-config-form="campaignConfigForm"
            :campaign-name="campaignName"
            :campaign-key="campaignKey"
        />
        <CampaignConfigContent />
    </div>
</template>

<script setup lang="ts">

const { fetchCampaignConfig } = useCampaignApi();
const scenarioTableStore = useScenarioTableStore();
const route = useRoute();

const campaignConfigForm = ref<CampaignConfigurationForm>();
const campaignName = ref<string>();
const campaignKey = ref<string>();

onMounted(async () => {
    try {
        campaignKey.value = route.params.id as string;
        const campaignConfig = await fetchCampaignConfig(campaignKey.value);
        campaignName.value = campaignConfig.name;
        campaignConfigForm.value = CampaignHelper.toCampaignConfigForm(campaignConfig);
        console.log(campaignConfigForm.value)
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
        ErrorHelper.handleHttpResponseError(error)
    }

})
</script>
