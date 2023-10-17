<template>
    <BaseHeader>
        <div class="flex space-between items-center full-width">
            <div class="flex items-center">
                <BaseIcon icon="/icons/icon-arrow-left-black.svg" class="cursor-pointer icon-link pr-2" @click="navigateTo('/reports')" />
                <BaseTitle v-model:content="reportName" :editable="true" />
            </div>
            <div class="flex items-center">
                <BaseSwitch
                    @checkedChange="handleCheckedChange"
                    :numberOfSelectedItems="selectedRowKeys.length"
                />
                <BaseSearch
                    class="ml-2"
                    v-model="searchQuery"
                    placeholder="Search campaigns..."
                    size="large"
                    @search="handleSearch"
                />
                <BaseButton
                    class="ml-2"
                    text="Compare"
                    @click="handleCompareReportBtnClick"
                    :disabled="!(selectedRowKeys.length > 0 || campaignPatterns.length > 0)"
                />
            </div>
        </div>
    </BaseHeader>
    <div class="page-content-container">
        <CampaignsPatternInput
            :preset-campaign-patterns="presetCampaignPatterns"
            @campaignPatternsChange="handleCampaignPatternsChange($event)"
        />
    </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { ReportCreationAndUpdateRequest } from 'utils/report';

const { createReport, updateReport } = useReportApi();

const campaignsTableStore = useCampaignsTableStore();
const { selectedRowKeys } = storeToRefs(campaignsTableStore);

const searchQuery = ref("");
const reportName = ref("New Report");
const campaignPatterns = ref<string[]>([]);
const presetCampaignPatterns = ref("");


const handleCampaignPatternsChange = (patterns: string[]) => {
    campaignPatterns.value = patterns;
}

const handleSearch = () => {
    campaignsTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(searchQuery.value),
        currentPageIndex: 0
    });
    campaignsTableStore.fetchCampaignsTableDataSource();
}

const handleCheckedChange = async (checked: boolean) => {
    if (checked) {
        campaignsTableStore.$patch({
            currentPageIndex: 0,
            dataSource: campaignsTableStore.selectedRows,
            totalElements: campaignsTableStore.selectedRows.length
        })
    } else {
        campaignsTableStore.$patch({
            currentPageIndex: 0
        })
        try {
            await campaignsTableStore.fetchCampaignsTableDataSource()
        } catch (error) {
            ErrorHelper.handleHttpResponseError(error)
        }
    }
}

const handleCompareReportBtnClick = async () => {
    const reportCreationRequest: ReportCreationAndUpdateRequest = {
        displayName: reportName.value,
        sharingMode: SharingMode.WRITE,
        campaignKeys: campaignsTableStore.selectedRowKeys,
        campaignNamesPatterns: campaignPatterns.value,
        scenarioNamesPatterns: [],
        dataComponents: []
    };

    try {
        const report = await createReport(reportCreationRequest);
        navigateTo(`/reports/${report.reference}`);
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error);
    }
}

</script>
