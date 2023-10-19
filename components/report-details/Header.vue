<template>
    <BaseHeader>
        <div class="flex space-between items-center full-width">
            <div class="flex items-center">
                <BaseIcon icon="/icons/icon-arrow-left-black.svg" class="cursor-pointer icon-link pr-2" @click="navigateTo('/reports')" />
                <BaseTitle v-model:content="reportName" :editable="true" />
            </div>
            <div class="flex items-center">
                <BaseButton
                    class="ml-2"
                    text="Save"
                    @click="handleSaveReportBtnClick"
                />
            </div>
        </div>
    </BaseHeader>
</template>


<script setup lang="ts">
import { storeToRefs } from 'pinia';

const emit = defineEmits<{
    (e: "saved"): void
}>()

const reportDetailsStore = useReportDetailsStore();
const { updateReport } = useReportApi();
const { reportName } = storeToRefs(reportDetailsStore);

const handleSaveReportBtnClick = async () => {
    // Calls the API to update the campaigns
    const request: ReportCreationAndUpdateRequest = {
        displayName: reportDetailsStore.reportName,
        campaignKeys: reportDetailsStore.campaignKeys,
        campaignNamesPatterns: reportDetailsStore.campaignNamesPatterns,
        sharingMode: SharingModeConstant.WRITE,
        scenarioNamesPatterns: reportDetailsStore.reportDetails?.scenarioNamesPatterns,
        dataComponents: reportDetailsStore.dataComponents.map(dataComponent => ({
            dataSeriesReferences: dataComponent.datas.map(d => d.reference),
            type: dataComponent.type
        })),
    };
    try {
        await updateReport(reportDetailsStore.reportDetails!.reference, request);
        NotificationHelper.success(`Report ${reportDetailsStore.reportName} has been successfully updated.`)
        emit("saved")
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }   
}

</script>
