<template>
    <BaseHeader>
        <div class="flex items-center full-width space-between">
            <div class="flex items-center">
                <BaseIcon icon="/icons/icon-arrow-left-black.svg" class="cursor-pointer icon-link pr-2" @click="handleBackBtnClick"/>
                <h2>{{ name }}</h2>
            </div>
            <div class="flex items-center">
                <ScenarioDropdown
                    v-if="scenarioNames.length > 0"
                    :scenarioNames="scenarioNames"
                    :selectedScenarioNames="selectedScenarioNames"
                    @scenarioChange="handleScenarioChange($event)"
                />
            </div>
        </div>
    </BaseHeader>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';

const props = defineProps<{
    campaignDetails: CampaignExecutionDetails
}>();

const route = useRoute();
const router = useRouter();
const campaignDetailsStore = useCampaignDetailsStore();
const { selectedScenarioNames } = storeToRefs(campaignDetailsStore);

const name = ref('');
const scenarioNames = ref<string[]>([]);

onMounted(() => {
    name.value = props.campaignDetails.name;
    scenarioNames.value = props.campaignDetails.scenarios?.length ? props.campaignDetails.scenarios.map(s => s.name) : [];
})

const handleBackBtnClick = () => {
    navigateTo('/campaigns')
}

const handleScenarioChange = async (scenarioNames: string[]) => {
    // Updates the selected scenarios from the store
    campaignDetailsStore.$patch({
        selectedScenarioNames: scenarioNames
    })
    // Updates the scenario from the url query params
    const currentQueryParams = route.query;
    const newQueryParams = {
      ...currentQueryParams,
      scenarios: scenarioNames.join(',')
    }
    router.replace({
      query: newQueryParams
    }); 
    // Updates the chart
    try {
        await campaignDetailsStore.updateChart();
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error);
    }
}

</script>
