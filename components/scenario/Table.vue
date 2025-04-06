<template>
  <BaseTable
    :data-source="dataSource"
    :table-column-configs="ScenariosTableConfig.TABLE_COLUMNS"
    :total-elements="totalElements"
    :page-size="pageSize"
    :current-page-index="currentPageIndex"
    :disable-row="disableRow"
    :row-selection-enabled="true"
    :selected-row-keys="selectedRowKeys"
    :all-data-source-included="true"
    rowKey="name"
    row-class="group"
    @page-change="handlePaginationChange"
    @selection-change="handleSelectionChange"
    @refresh="handleRefreshBtnClick"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'description'">
        <span>{{ record.description ?? '-' }}</span>
      </template>
    </template>
    <template #actionCell="{ record }">
      <div 
        class="flex items-center cursor-pointer h-8 invisible group-hover:visible"
        @click="handleConfigureBtnClick(record as ScenarioSummary)"
      >
        <BaseTooltip text="Configure">
            <BaseIcon 
              icon="qls-icon-setting"
              class="text-2xl text-gray-600 hover:text-primary-500"
            />
        </BaseTooltip>
      </div>
    </template>
  </BaseTable> 
  <ScenarioConfigDrawer
    v-if="configDrawerOpen"
    v-model:open="configDrawerOpen"
    :scenario="selectedScenarioSummary"
    :configuration="campaignConfiguration"
    :scenario-form="selectedScenarioConfigForm"
    @submit="handleScenarioConfigFormSubmit($event)"
  />
</template>

<script setup lang="ts">

const props = defineProps<{
  campaignConfiguration: DefaultCampaignConfiguration;
}>();

const { fetchScenarios } = useScenarioApi();

const toastStore = useToastStore();
const scenarioTableStore = useScenarioTableStore();

const { selectedRowKeys, dataSource, totalElements, pageSize, currentPageIndex } = storeToRefs(scenarioTableStore);

let selectedScenarioSummary: ScenarioSummary;

const selectedScenarioConfigForm = ref<ScenarioConfigurationForm>();

const configDrawerOpen = ref(false);

onMounted(async () => {
  try {
    // Fetches all scenarios and sets to the scenario table store.
    const scenarios = await fetchScenarios();
    scenarioTableStore.$patch({
      dataSource: scenarios,
      allScenarios: scenarios,
      totalElements: scenarios.length,
    });
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) });
  }
});

onBeforeUnmount(() => {
  scenarioTableStore.$reset();
});

const handleRefreshBtnClick = async () => {
  await scenarioTableStore.refreshScenarios();
}

const disableRow = (record: ScenarioSummary) => {
  if (!props.campaignConfiguration) return false;

  return !selectedRowKeys.value.includes(record.name)
    && selectedRowKeys.value.length >= props.campaignConfiguration.validation.maxScenariosCount;
}

const handlePaginationChange = (pageIndex: number) => {
  scenarioTableStore.$patch({
    currentPageIndex: pageIndex
  });
}

const handleSelectionChange = (tableSelection: TableSelection) => {
  scenarioTableStore.$patch({
    selectedRows: tableSelection.selectedRows,
    selectedRowKeys: tableSelection.selectedRowKeys
  });
}

const handleConfigureBtnClick = (scenarioSummary: ScenarioSummary) => {
  selectedScenarioSummary = scenarioSummary;
  selectedScenarioConfigForm.value = scenarioTableStore.scenarioConfig[scenarioSummary.name]
  configDrawerOpen.value = true;
};

const handleScenarioConfigFormSubmit = (form: ScenarioConfigurationForm) => {
  scenarioTableStore.scenarioConfig[selectedScenarioSummary!.name] = form;
  if (!scenarioTableStore.selectedRows.some(r => r.name === selectedScenarioSummary!.name)) {
    scenarioTableStore.selectedRows.push(selectedScenarioSummary)
  }

  if (!scenarioTableStore.selectedRowKeys.some(rowKey => rowKey === selectedScenarioSummary!.name)) {
    scenarioTableStore.selectedRowKeys.push(selectedScenarioSummary.name)
  }
}

</script>
