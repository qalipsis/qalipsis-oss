<template>
    <div v-if="campaignConfiguration">
        <CampaignConfigHeader 
            :campaignConfiguration="campaignConfiguration"
        />
        <CampaignConfigContent
            :campaignConfiguration="campaignConfiguration"    
        />
    </div>
</template>

<script setup lang="ts">

const { fetchCampaignConfiguration } = useConfigurationApi();

const campaignConfiguration = ref<DefaultCampaignConfiguration>();

onMounted(async () => {
    // Prepares the default campaign configuration for configuring.
    campaignConfiguration.value = await fetchCampaignConfiguration();
})

</script>
