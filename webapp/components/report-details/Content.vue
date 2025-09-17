<template>
  <BaseContentWrapper>
    <div class="shadow-md pt-2 pb-2 pr-4 pl-4 mb-2">
      <div class="my-4">
        <ReportDetailsDescription
            :presetDescription="description"
            @change="handleDescriptionValueChange($event)"
        />
      </div>
      <div class="flex items-center justify-between mb-4">
        <div class="flex items-center flex-wrap">
          <template v-for="campaignOption in campaignOptions" :key="campaignOption.key">
            <div
                :class="{
                                'border-purple-600': campaignOption.isActive,
                                'border-gray-300': !campaignOption.isActive,
                            }"
                class="h-10 px-3 py-2 text-base rounded-md min-w-32 flex items-center justify-center border border-solid cursor-pointer mr-2 mb-1"
                @click="handleCampaignOptionClick(campaignOption)"
            >
              <svg height="24" width="40">
                <polyline
                    :class="{ 'stroke-gray-400': !campaignOption.isActive, 'stroke-purple-600': campaignOption.isActive }"
                    :stroke-dasharray="campaignOption.strokeDashArray"
                    class="fill-none stroke-2"
                    points="0,12 8,0 16,24 24,0 32,24 40,0"
                />
              </svg>
              <span class="pl-2">{{ campaignOption.name }}</span>
            </div>
          </template>
          <BaseButton
              text="Select campaigns"
              btn-style="outlined"
              icon="qls-icon-edit"
              class="mr-2 mb-1"
              @click="handleCampaignSelectBtnClick"
          />
        </div>
        <div>
          <ScenarioDropdown
              v-if="scenarioNames.length > 0"
              :scenarioNames="scenarioNames"
              :selectedScenarioNames="selectedScenarioNames"
              @scenarioChange="handleScenarioChange($event)"
          />
        </div>
      </div>
      <template v-for="activeCampaignOption in activeCampaignOptions" :key="activeCampaignOption.key">
        <div
            v-if="activeCampaignOption.isActive && activeCampaignOption.enrichedScenarioReports.length > 0"
            class="px-6 py-2 bg-gray-50 dark:bg-gray-950 rounded-md mb-4"
        >
          <div class="flex items-center h-8 text-xl font-medium">
            <span>{{ activeCampaignOption.name }}</span>
          </div>
          <div class="flex items-center">
            <ScenarioDetails
                :scenario-reports="activeCampaignOption.enrichedScenarioReports"
            />
          </div>
        </div>
      </template>
    </div>
    <div class="shadow-md pt-2 pb-2 pr-4 pl-4">
      <div class="flex items-center">
        <BaseButton
            text="Add chart"
            icon="qls-icon-plus"
            btn-style="outlined"
            @click="handleAddChartBtnClick"
        />
        <BaseButton
            text="Add table"
            class="ml-4"
            icon="qls-icon-plus"
            btn-style="outlined"
            @click="handleAddTableBtnClick"
        />
      </div>
      <div class="pt-2 pb-2 pr-4 pl-4">
        <div v-for="(dataComponent, idx) in dataComponents" :key="dataComponent.id">
          <template v-if="dataComponent.type === DataComponentTypeConstant.DATA_TABLE">
            <ReportDetailsTableData
                :dataSeries="dataComponent.datas"
                :component-index="idx"
            />
          </template>
          <template v-if="dataComponent.type === DataComponentTypeConstant.DIAGRAM">
            <ReportDetailsChartData
                :dataSeries="dataComponent.datas"
                :component-index="idx"
            />
          </template>
        </div>
      </div>
    </div>
    <ReportDetailsCampaignSelectDrawer
        v-if="campaignSelectDrawerOpen"
        v-model:open="campaignSelectDrawerOpen"
        :campaignPatterns="reportDetails?.campaignNamesPatterns"
        :description="reportDetails?.description"
        :report="reportDetails"
        @saved="handleReportUpdated"
    />
  </BaseContentWrapper>
</template>

<script setup lang="ts">

const emit = defineEmits<{
  (e: "saved"): void
}>()

const reportDetailsStore = useReportDetailsStore();
const {
  campaignOptions,
  scenarioNames,
  selectedScenarioNames,
  dataComponents,
  reportDetails,
  description
} = storeToRefs(reportDetailsStore);

const activeCampaignOptions = computed(() => campaignOptions.value.filter(campaignOption => campaignOption.isActive))
const campaignSelectDrawerOpen = ref(false);

const handleDescriptionValueChange = (description: string) => {
  reportDetailsStore.$patch({
    description: description
  })
}

const handleAddChartBtnClick = () => {
  _addDataComponent(DataComponentTypeConstant.DIAGRAM);
}

const handleAddTableBtnClick = () => {
  _addDataComponent(DataComponentTypeConstant.DATA_TABLE);
}

const handleCampaignOptionClick = (campaignOption: CampaignOption) => {
  campaignOption.isActive = !campaignOption.isActive;
}

const handleScenarioChange = (scenarioNames: string[]) => {
  selectedScenarioNames.value = scenarioNames
  reportDetailsStore.$patch({
    campaignOptions: campaignOptions.value.map(campaignOption => ({
      ...campaignOption,
      enrichedScenarioReports: ScenarioHelper.getSelectedScenarioReports(scenarioNames, campaignOption),
    }))
  })
}

const handleCampaignSelectBtnClick = () => {
  campaignSelectDrawerOpen.value = true
}

const handleReportUpdated = () => {
  emit("saved");
}

const _addDataComponent = (dataComponentType: DataComponentType) => {
  const dataComponent: DataComponent = {
    id: Date.now(),
    datas: [],
    type: dataComponentType
  }
  const existingDataComponents = dataComponents?.value ?? [];
  reportDetailsStore.$patch({
    dataComponents: [dataComponent, ...existingDataComponents]
  })
}

</script>
