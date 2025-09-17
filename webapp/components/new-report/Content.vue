<template>
  <BaseContentWrapper>
    <CampaignsTable
        :rowSelectionEnabled="true"
        :name-clickable="false"
        :maxSelectedRows="10"
        :extraQueryParams="{
                'excluded-status': 'SCHEDULED'
            }"
    />
  </BaseContentWrapper>
</template>
