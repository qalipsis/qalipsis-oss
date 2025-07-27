<template>
    <template v-if="isReady">
        <ReportDetailsHeader
            @saved="handleReportSaved"
        />
        <ReportDetailsContent
            @saved="handleReportContentUpdated"
        />
        <BaseModal
            title="Changes are not yet saved"
            v-model:open="modalOpen"
            @confirmBtnClick="handleConfirmBtnClick">
            <span>You have unsaved changes for the report. Do you really want to leave the page without saving?</span>
        </BaseModal>
    </template>
</template>

<script setup lang="ts">
import type { RouteLocationNormalized } from '#vue-router';

const { fetchReportDetails } = useReportApi();
const { fetchMultipleCampaignsDetails } = useCampaignApi();
const { fetchAllDataSeries } = useDataSeriesApi();

const route = useRoute();
const userStore = useUserStore();
const toastStore = useToastStore();
const reportDetailsStore = useReportDetailsStore();
const isReady = ref(false);
const modalOpen = ref(false);
let redirectPath = "";
let shouldDiscardChanges = false;
let hasSavedChanges = false;

onMounted(async () => {
    await _fetchReport();
    window.addEventListener('beforeunload', (e) => {
        if (reportDetailsStore.hasUnsavedChanges && !shouldDiscardChanges && !hasSavedChanges) {
            e.preventDefault();
            // Chrome requires returnValue to be set
            e.returnValue = ''
        }
    })
})

onBeforeRouteLeave((to: RouteLocationNormalized, _: RouteLocationNormalized) => {
    redirectPath = to.path;
    if (reportDetailsStore.hasUnsavedChanges && !shouldDiscardChanges && !hasSavedChanges) {
        modalOpen.value = true
        return false;
    }
})

onBeforeUnmount(() => {
    reportDetailsStore.$reset();
})

const handleConfirmBtnClick = () => {
    shouldDiscardChanges = true;
    navigateTo(redirectPath);
}

const _fetchReport = async () => {
    try {
        isReady.value = false;
        const reportReference = route.params.id as string;
        const reportDetails: DataReport = await fetchReportDetails(reportReference);
        let campaignOptions: CampaignOption[] = [];
        const campaignKeys = reportDetails.resolvedCampaigns?.length
            ? reportDetails.resolvedCampaigns.map(campaign => campaign.key)
            : [];
        const campaigns: CampaignExecutionDetails[] = reportDetails.resolvedCampaigns ? await fetchMultipleCampaignsDetails(campaignKeys): [];
        const allDataSeries: DataSeries[] = await fetchAllDataSeries();
        const allDataSeriesOptions: DataSeriesOption[] = SeriesHelper.toDataSeriesOptions(allDataSeries, []);
        
        campaignOptions = campaigns ? campaigns.map((campaignDetail, index) => ({
            ...campaignDetail,
            enrichedScenarioReports: ScenarioHelper.getSelectedScenarioReports(reportDetails.resolvedScenarioNames!, campaignDetail),
            strokeDashArray: index + 1,
            isActive: true
        })): [];
        reportDetailsStore.$patch({
            reportDetails: reportDetails,
            reportName: reportDetails.displayName,
            campaignOptions: campaignOptions,
            dataComponents: structuredClone(reportDetails.dataComponents),
            scenarioNames: reportDetails.resolvedScenarioNames ? [...reportDetails.resolvedScenarioNames] : [],
            selectedScenarioNames: reportDetails.resolvedScenarioNames ? [...reportDetails.resolvedScenarioNames] : [],
            campaignNamesPatterns: reportDetails.campaignNamesPatterns ?? [],
            description: reportDetails.description ?? '',
            campaignKeys: reportDetails.resolvedCampaigns?.map(c => c.key),
            allDataSeriesOptions: allDataSeriesOptions
        });
        isReady.value = true;
    } catch (error) {
        toastStore.error({ text: ErrorHelper.getErrorMessage(error) });
    }
}

const handleReportSaved = () => {
    hasSavedChanges = true;
}

const handleReportContentUpdated = () => {
    hasSavedChanges = true;
    _fetchReport()
}

</script>
