<template>
    <BaseContentWrapper>
        <ScenarioTable 
            :campaignConfiguration="campaignConfiguration"
        />
    </BaseContentWrapper>
</template>

<script setup lang="ts">
defineProps<{
  campaignConfiguration: DefaultCampaignConfiguration,
}>();
</script>
