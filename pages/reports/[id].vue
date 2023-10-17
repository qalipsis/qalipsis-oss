<template>
    <template v-if="isReady">
        <ReportDetailsHeader
            @saved="handleReportSaved"
        />
        <ReportDetailsContent
            @afterUpdated="handleReportContentUpdated"
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
import { CampaignExecutionDetails, CampaignOption } from "utils/campaign";
import { Report } from "../../utils/report";
import { DataSeries, DataSeriesOption } from "utils/series";
import { RouteLocationNormalized } from ".nuxt/vue-router";

const { fetchReportDetails } = useReportApi();
const { fetchMultipleCampaignsDetails } = useCampaignApi();
const { fetchAllDataSeries } = useDataSeriesApi();

const route = useRoute();
const userStore = useUserStore();
const reportDetailsStore = useReportDetailsStore();
const isReady = ref(false);
const modalOpen = ref(false);
let redirectPath = "";
let shouldDiscardChanges = false;
let hasSavedChanges = false;

onMounted(async () => {
    window.addEventListener('beforeunload', (e) => {
        if (reportDetailsStore.hasUnsavedChanges && !shouldDiscardChanges && !hasSavedChanges) {
            e.preventDefault();
            // Chrome requires returnValue to be set
            e.returnValue = ''
        }
    })
    _fetchReport();
})

watch(() => userStore.currentTenantReference, () => {
    navigateTo('/reports');
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

const handleWindowUnload = (e?: Event) => {
    
}


const handleConfirmBtnClick = () => {
    shouldDiscardChanges = true;
    navigateTo(redirectPath);
}

const _fetchReport = async () => {
    try {
        isReady.value = false;
        const reportReference = route.params.id as string;
        const reportDetails: Report = await fetchReportDetails(reportReference);
        let campaignOptions: CampaignOption[] = [];
        const campaignKeys = reportDetails.resolvedCampaigns ? reportDetails.resolvedCampaigns.map(campaign => campaign.key) : [];
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
            campaignKeys: reportDetails.resolvedCampaigns?.map(c => c.key),
            allDataSeriesOptions: allDataSeriesOptions
        });
        isReady.value = true;
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error);
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
