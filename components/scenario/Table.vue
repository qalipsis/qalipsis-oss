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
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'description'">
        <span>{{ record.description ?? '-' }}</span>
      </template>
    </template>
    <template #actionCell="{ record }">
      <div 
        class="flex items-center cursor-pointer h-8 invisible group-hover:visible"
        :class="TailwindClassHelper.primaryColorFilterHoverClass"
        @click="handleConfigureBtnClick(record as ScenarioSummary)"
      >
          <BaseIcon icon="/icons/icon-setting-grey.svg" />
          <span> Configure </span>
      </div>
    </template>
  </BaseTable> 
  <ScenarioConfigDrawer
    v-if="configDrawerOpen"
    v-model:open="configDrawerOpen"
    :scenario="selectedScenarioSummary"
    :zone-options="zoneOptions"
    :configuration="campaignConfiguration"
    :scenario-form="selectedScenarioConfigForm"
    @submit="handleScenarioConfigFormSubmit($event)"
  />
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";

const { fetchCampaignConfiguration } = useConfigurationApi();
const { fetchScenarios } = useScenarioApi();
const { fetchZones } = useZonesApi();
const scenarioTableStore = useScenarioTableStore();

const { selectedRowKeys, dataSource, totalElements, pageSize, currentPageIndex } = storeToRefs(scenarioTableStore);

let campaignConfiguration: DefaultCampaignConfiguration;
let selectedScenarioSummary: ScenarioSummary;
const zoneOptions = ref<FormMenuOption[]>([]);
const selectedScenarioConfigForm = ref<ScenarioConfigurationForm>();

const configDrawerOpen = ref(false);

onMounted(async () => {
  try {
    const allScenarioSummary = await fetchScenarios();
    const zones = await fetchZones();
    campaignConfiguration = await fetchCampaignConfiguration();
    zoneOptions.value = zones.map(zone => ({
      label: zone.title,
      value: zone.key
    }))
    scenarioTableStore.$patch({
      allScenarioSummary: allScenarioSummary,
      dataSource: allScenarioSummary,
      totalElements: allScenarioSummary.length,
      defaultCampaignConfiguration: campaignConfiguration
    });
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
});

onBeforeUnmount(() => {
  scenarioTableStore.$reset();
});

const disableRow = (record: ScenarioSummary) => {
  if (!campaignConfiguration) return false;

  return !selectedRowKeys.value.includes(record.name)
    && selectedRowKeys.value.length >= campaignConfiguration.validation.maxScenariosCount;
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
