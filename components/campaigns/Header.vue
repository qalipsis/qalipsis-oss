<template>
    <BaseHeader>
        <div class="flex items-center w-full justify-between">
            <BaseTitle content="Campaigns" />
            <div class="flex items-center">
                <BaseSearch 
                    v-model="campaignSearchQuery" 
                    placeholder="Search campaigns..."
                    size="large"
                    :collapsable="true"
                    @search="handleSearch"
                />
                <BaseButton 
                    class="ml-2"
                    text="New campaign" 
                    btn-style="outlined"
                    :icon="'/icons/icon-plus-grey.svg'"
                    @click="handleCreateCampaignBtnClick" 
                />
            </div>
        </div>
    </BaseHeader>
</template>

<script setup lang="ts">

const campaignsTableStore = useCampaignsTableStore();
const campaignSearchQuery = ref('');

const handleSearch = () => {
    campaignsTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(campaignSearchQuery.value),
        currentPageIndex: 0
    });
    campaignsTableStore.fetchCampaignsTableDataSource();
}

const handleCreateCampaignBtnClick = () => {
    navigateTo('campaigns/new');
}

</script>
