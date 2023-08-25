<template>
    <BaseHeader>
        <div class="flex items-center full-width space-between">
            <h2>Campaigns</h2>
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
                    :btn-style="'stroke'"
                    :icon="'/icons/icon-plus-grey.svg'"
                    @click="handleCreateCampaignBtnClick" 
                />
            </div>
        </div>
    </BaseHeader>
</template>

<script setup lang="ts">

const campaignSearchQuery = ref('');

const handleSearch = () => {

}

const handleCreateCampaignBtnClick = () => {

}

</script>

<style scoped lang="scss">

</style>