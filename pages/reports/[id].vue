<template>
    <ReportDetailsHeader />
    <ReportDetailsContent />
</template>

<script setup lang="ts">
const { fetchReportDetails } = useReportApi();
const { fetchMultipleCampaignsDetails } = useCampaignApi();

const route = useRoute();
const userStore = useUserStore();
const reportDetailsStore = useReportDetailsStore();

onMounted(async () => {
    try {
        const reportReference = route.params.id as string;
        const reportDetails = await fetchReportDetails(reportReference);
        let campaignOptions = [];
        if (reportDetails.resolvedCampaigns) {
            const campaignKeys = reportDetails.resolvedCampaigns.map(campaign => campaign.key);
            const campaigns: CampaignExecutionDetails[] = await fetchMultipleCampaignsDetails(campaignKeys);
            campaignOptions = campaigns.map((campaignDetail, index) => ({
                ...campaignDetail,
                scenarioReports: ScenarioHelper.getSelectedScenarioReports(reportDetails.resolvedScenarioNames, campaignDetail),
                strokeDashArray: index,
                isActive: true
            }));
            console.log(campaignOptions)
        }
        console.log(reportDetails)
    } catch (error) {
        
    }
})

watch(() => userStore.currentTenantReference, () => {
    navigateTo('/reports');
})
</script>

<style scoped>

</style>