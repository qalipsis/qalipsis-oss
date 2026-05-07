<template>
  <BaseHeader>
    <div class="flex items-center w-full justify-between">
      <BaseTitle content="Campaigns"/>
      <div class="flex items-center">
        <BaseSearch
            placeholder="Search campaigns..."
            size="large"
            :collapsable="true"
            @search="handleSearch"
        />
        <BaseButton
            class="ml-2"
            text="New campaign"
            btn-style="outlined"
            icon="qls-icon-plus"
            @click="handleCreateCampaignBtnClick"
        />
      </div>
    </div>
  </BaseHeader>
  <BaseContentWrapper>
    <div class="mb-4">
      <BaseCard>
        <CampaignStatisticVerticalBarChart/>
      </BaseCard>
    </div>
  </BaseContentWrapper>
  <BaseContentWrapper>
    <CampaignsTable :actionsEnabled="true"/>
  </BaseContentWrapper>
</template>

<script setup lang="ts">
const campaignsTableStore = useCampaignsTableStore()

const { handleSearch } = useTableSearch(
  (query) => campaignsTableStore.$patch({ filter: query, currentPageIndex: 0 }),
  campaignsTableStore.fetchCampaignsTableDataSource
)

const handleCreateCampaignBtnClick = () => {
  navigateTo('campaigns/new')
}
</script>
