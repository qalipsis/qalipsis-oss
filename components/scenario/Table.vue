<template>
  <a-table
    :data-source="dataSource"
    :columns="tableColumnConfigs"
    :show-sorter-tooltip="false"
    :ellipsis="true"
    rowKey="name"
    :pagination="pagination"
    :rowSelection="rowSelection"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'actions'">
        <div class="table-action-item-wrapper">
            <div class="flex items-center cursor-pointer table-action-item" @click="handleConfigureBtnClick(record)">
                <BaseIcon icon="/icons/icon-setting-grey.svg" />
                <span> Configure </span>
            </div>
        </div>
      </template>
    </template>
  </a-table>
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
import { DefaultCampaignConfiguration } from "utils/configuration";
import { FormMenuOption } from "utils/form";
import { ScenarioConfigurationForm, ScenarioSummary } from "utils/scenario";

const { fetchCampaignConfiguration } = useConfigurationApi();
const { fetchScenarios } = useScenarioApi();
const { fetchZones } = useZonesApi();
const scenarioTableStore = useScenarioTableStore();
const { selectedRowKeys, dataSource, totalElements } =
  storeToRefs(scenarioTableStore);

const tableColumnConfigs = ScenarioHelper.getTableColumnConfigs();
let campaignConfiguration: DefaultCampaignConfiguration;
let selectedScenarioSummary: ScenarioSummary;
const zoneOptions = ref<FormMenuOption[]>([]);
const selectedScenarioConfigForm = ref<ScenarioConfigurationForm>();

const pagination = reactive({
  pageSize: 10,
  total: totalElements,
  ...TableHelper.sharedPaginationProperties,
});

const rowSelection = reactive({
  hideSelectAll: true,
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (selectedRowKeys: string[], selectedRows: ScenarioSummary[]) => {
    scenarioTableStore.$patch({
      selectedRows: selectedRows,
      selectedRowKeys: selectedRowKeys,
    });
  },
  getCheckboxProps: (record: ScenarioSummary) => {
    return {
      disabled:
        !selectedRowKeys.value.includes(record.name) &&
        selectedRowKeys.value.length >=
          campaignConfiguration.validation.maxScenariosCount,
    };
  },
});

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
    });
  } catch (error) {
    ErrorHelper.handleHttpRequestError(error);
  }
});

onBeforeUnmount(() => {
  scenarioTableStore.$reset();
});

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
