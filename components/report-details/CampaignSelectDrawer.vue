<template>
    <BaseDrawer
        title="Select campaigns"
        confirm-btn-text="Compare"
        :open="open"
        @close="handleDrawerCloseEvent"
        :width="1200"
        @confirm-btn-click="handleCampaignSelectBtnClick"
    >   
        <div class="flex content-end">
            <BaseSearch
                v-model="searchQuery"
                placeholder="Search campaigns..."
                @search="handleSearch"
            />
        </div>
        <CampaignsPatternInput
            :preset-campaign-patterns="presetCampaignPatterns"
            @campaign-patterns-change="handleCampaignPatternsChange($event)"
        />
        <CampaignsTable 
            :rowSelectionEnabled="true"
            :name-clickable="false"
            :maxSelectedRows="10"
        />
    </BaseDrawer>
</template>

<script setup lang="ts">
import { ReportCreationAndUpdateRequest } from 'utils/report';

const props = defineProps<{
    open: boolean,
    campaignPatterns: string[],
}>();
const emit = defineEmits<{
    (e: "update:open", v: boolean): void,
    (e: "saved"): void
}>()

const { updateReport } = useReportApi();
const campaignsTableStore = useCampaignsTableStore();
const reportDetailsStore = useReportDetailsStore();

const searchQuery = ref("");
const presetCampaignPatterns = computed(() => props.campaignPatterns.length ? props.campaignPatterns.join(",") : "")

onMounted(() => {
    campaignsTableStore.$patch({
        selectedRowKeys: reportDetailsStore.campaignKeys
    })
})

const handleCampaignSelectBtnClick = async () => {
    const request: ReportCreationAndUpdateRequest = {
        displayName: reportDetailsStore.reportName,
        sharingMode: SharingMode.WRITE,
        campaignKeys: campaignsTableStore.selectedRowKeys,
        campaignNamesPatterns: reportDetailsStore.campaignNamesPatterns,
        scenarioNamesPatterns: reportDetailsStore.reportDetails?.scenarioNamesPatterns ?? [],
        dataComponents: reportDetailsStore.dataComponents ? reportDetailsStore.dataComponents.map(dataComponent => ({
            dataSeriesReferences: dataComponent.datas.map(d => d.reference),
            type: dataComponent.type
        })) : [],
    };
    try {
        await updateReport(reportDetailsStore.reportDetails!.reference, request);
        emit("saved");
        NotificationHelper.success(`Report ${reportDetailsStore.reportName} has been successfully updated.`)
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
}

const handleDrawerCloseEvent = () => {
    emit("update:open", false);
}

const handleCampaignPatternsChange = (campaignNamesPatterns: string[]) => {
    reportDetailsStore.$patch({
        campaignNamesPatterns: campaignNamesPatterns
    })
}

const handleSearch = () => {
    campaignsTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(searchQuery.value),
        currentPageIndex: 0
    });
    campaignsTableStore.fetchCampaignsTableDataSource();
}


</script>
