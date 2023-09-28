<template>
    <BaseHeader>
        <div class="flex space-between items-center full-width">
            <div class="flex items-center">
                <BaseIcon 
                    icon="/icons/icon-arrow-left-black.svg"
                    class="cursor-pointer icon-link pr-2"
                    @click="navigateTo('/campaigns')" />
                <BaseTitle v-model:content="campaignName" :editable="true" />
            </div>
            <div class="flex items-center">
                <BaseSwitch
                    @checkedChange="handleCheckedChange"
                    :numberOfSelectedItems="selectedRowKeys.length"
                />
                <div class="cursor-pointer pl-2" @click="handleSettingBtnClick">
                    <BaseIcon
                        icon="/icons/icon-setting-grey.svg"
                    />
                </div>
                <BaseSearch
                    class="ml-2"
                    v-model="searchQuery"
                    placeholder="Search scenario..."
                    size="large"
                    @search="handleSearch"
                />
                <BaseButton
                    class="ml-2"
                    text="Run immediately"
                    @click="handleRunBtnClick"
                    :disabled="selectedRowKeys.length === 0"
                />
            </div>
        </div>
    </BaseHeader>
    <NewCampaignConfigDrawer
        v-if="open"
        v-model:open="open"
    />
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';

const scenarioTaleStore = useScenarioTableStore();
const { selectedRowKeys } = storeToRefs(scenarioTaleStore);

const campaignName = ref("New Campaign");
const searchQuery = ref("");
const open = ref(false);

const handleSearch = () => {
    if (searchQuery.value) {
        const query = SearchHelper.getSanitizedQuery(searchQuery.value);
        const filteredScenarioSummary = SearchHelper.performFuzzySearch(query, scenarioTaleStore.allScenarioSummary, ["name"]);
        scenarioTaleStore.$patch({
            dataSource: filteredScenarioSummary,
            totalElements: filteredScenarioSummary.length
        })
    } else {
        scenarioTaleStore.$patch({
            dataSource: scenarioTaleStore.allScenarioSummary,
            totalElements: scenarioTaleStore.allScenarioSummary.length
        })
    }

}

const handleCheckedChange = (checked: boolean) => {
    scenarioTaleStore.$patch({
        dataSource: checked ? scenarioTaleStore.selectedRows : scenarioTaleStore.allScenarioSummary,
        totalElements: checked ? scenarioTaleStore.selectedRows.length : scenarioTaleStore.allScenarioSummary.length,
    })
}

const handleSettingBtnClick = () => {
    open.value = true;
}

const handleRunBtnClick = () => {
    
}

</script>
