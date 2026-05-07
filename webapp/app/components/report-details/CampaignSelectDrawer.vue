<template>
  <BaseDrawer
      title="Select campaigns"
      confirm-btn-text="Compare"
      :open="open"
      @close="handleDrawerCloseEvent"
      :width="1200"
      @confirm-btn-click="handleCampaignSelectBtnClick"
  >
    <section class="pl-2 pr-2 pt-2 pb-2">
      <div class="mt-2">
        <CampaignsPatternInput
            :preset-campaign-patterns="presetCampaignPatterns"
            @campaign-patterns-change="handleCampaignPatternsChange($event)"
        />
      </div>
      <div class="flex justify-end mt-4 mb-4">
        <BaseSearch
            placeholder="Search campaigns..."
            @search="handleSearch"
        />
      </div>
      <CampaignsTable
          :rowSelectionEnabled="true"
          :name-clickable="false"
          :maxSelectedRows="10"
      />
    </section>
  </BaseDrawer>
</template>

<script setup lang="ts">
const props = defineProps<{
  open: boolean,
  campaignPatterns?: string[],
}>();
const emit = defineEmits<{
  (e: "update:open", v: boolean): void,
  (e: "saved"): void
}>()

const {updateReport} = useReportApi();
const campaignsTableStore = useCampaignsTableStore();
const reportDetailsStore = useReportDetailsStore();
const toastStore = useToastStore();

const presetCampaignPatterns = computed(() => props.campaignPatterns?.length ? props.campaignPatterns.join(",") : "")

onMounted(() => {
  campaignsTableStore.$patch({
    selectedRowKeys: reportDetailsStore.campaignKeys
  })
})

const handleCampaignSelectBtnClick = async () => {
  const request: ReportCreationAndUpdateRequest = {
    displayName: reportDetailsStore.reportName,
    sharingMode: SharingModeConstant.WRITE,
    campaignKeys: campaignsTableStore.selectedRowKeys,
    description: reportDetailsStore.description,
    campaignNamesPatterns: reportDetailsStore.campaignNamesPatterns,
    scenarioNamesPatterns: reportDetailsStore.reportDetails?.scenarioNamesPatterns ?? [],
    dataComponents: reportDetailsStore.dataComponents ? reportDetailsStore.dataComponents.map(dataComponent => ({
      dataSeriesReferences: dataComponent.datas.map(d => d.reference),
      type: dataComponent.type
    })) : [],
  };
  try {
    await updateReport(reportDetailsStore.reportDetails!.reference, request);
    emit("update:open", false);
    emit("saved");
    toastStore.success({text: `Report ${reportDetailsStore.reportName} has been successfully updated.`})
  } catch (error) {
    toastStore.error({text: ErrorHelper.getErrorMessage(error)});
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

const handleSearch = (searchTerm: string) => {
  campaignsTableStore.$patch({
    filter: TableHelper.getSanitizedQuery(searchTerm),
    currentPageIndex: 0
  });
  campaignsTableStore.fetchCampaignsTableDataSource();
}

</script>
