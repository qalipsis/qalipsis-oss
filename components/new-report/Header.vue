<template>
    <BaseHeader>
        <div class="flex justify-between items-center w-full">
            <div class="flex items-center">
                <BaseIcon 
                    icon="qls-icon-arrow-back"
                    class="cursor-pointer pl-1 pr-5 hover:text-primary-500 text-2xl"
                    @click="navigateTo('/reports')"
                />
                <BaseTitle v-model:content="reportName" :editable="true" />
            </div>
            <div class="flex items-center">
                <BaseButton
                    class="ml-2"
                    text="Create"
                    @click="handleCreateReportBtnClick"
                    :disabled="!(selectedRowKeys.length > 0 || campaignPatterns.length > 0)"
                />
            </div>
        </div>
    </BaseHeader>
    <BaseContentWrapper>
        <ReportDetailsDescription
            :presetDescription="reportDescription"
            @change="handleDescriptionValueChange($event)"
        />
        <CampaignsPatternInput
            :preset-campaign-patterns="presetCampaignPatterns"
            @campaignPatternsChange="handleCampaignPatternsChange($event)"
        />
        <div class="mt-4 flex items-center justify-end w-full">
            <BaseSwitch
                @checkedChange="handleCheckedChange"
                :numberOfSelectedItems="selectedRowKeys.length"
            />
            <BaseSearch
                class="ml-2"
                placeholder="Search campaigns..."
                size="large"
                @search="handleSearch"
            />
        </div>
    </BaseContentWrapper>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';

const { createReport } = useReportApi();

const campaignsTableStore = useCampaignsTableStore();
const toastStore = useToastStore();

const { selectedRowKeys } = storeToRefs(campaignsTableStore);

const reportName = ref("New Report");
const reportDescription = ref("");
const campaignPatterns = ref<string[]>([]);
const presetCampaignPatterns = ref("");


const handleDescriptionValueChange = (description: string) => {
    reportDescription.value = description;
}

const handleCampaignPatternsChange = (patterns: string[]) => {
    campaignPatterns.value = patterns;
}

const handleSearch = (searchTerm: string) => {
    campaignsTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(searchTerm),
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
            toastStore.error({ text: ErrorHelper.getErrorMessage(error) });
        }
    }
}

const handleCreateReportBtnClick = async () => {
    const reportCreationRequest: ReportCreationAndUpdateRequest = {
        displayName: reportName.value,
        description: reportDescription.value,
        sharingMode: SharingModeConstant.WRITE,
        campaignKeys: campaignsTableStore.selectedRowKeys,
        campaignNamesPatterns: campaignPatterns.value,
        scenarioNamesPatterns: [],
        dataComponents: []
    };

    try {
        const report = await createReport(reportCreationRequest);
        navigateTo(`/reports/${report.reference}`);
    } catch (error) {
        toastStore.error({ text: ErrorHelper.getErrorMessage(error) });
    }
}

</script>
