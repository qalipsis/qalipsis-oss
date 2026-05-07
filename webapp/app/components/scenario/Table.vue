<template>
  <BaseTable
    :data-source="pagedData"
    :table-column-configs="ScenariosTableConfig.TABLE_COLUMNS"
    :total-elements="totalElements"
    :page-size="pageSize"
    :current-page-index="currentPageIndex"
    :disable-row="disableRow"
    :row-selection-enabled="rowSelectionEnabled"
    :selected-row-keys="selectedRowKeys"
    rowKey="name"
    @page-change="handlePageChange"
    @sorter-change="handleSorterChange"
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
        class="flex items-center cursor-pointer h-8 relative"
        @click="handleConfigureBtnClick(record as ScenarioSummary)"
      >
        <BaseTooltip text="Configure">
          <BaseIcon
            icon="qls-icon-setting"
            class="text-2xl text-gray-600 dark:text-gray-100 hover:text-primary-500"
          />
        </BaseTooltip>
        <div
          v-if="selectedRowKeys.includes(record.name)"
          class="absolute top-0 left-4"
        >
          <div v-if="!scenarioConfig[record.name]">
            <BaseIcon
              icon="qls-icon-error-fill"
              class="text-base text-gray-400 dark:text-gray-200"
            />
          </div>
          <div v-else>
            <BaseIcon
              icon="qls-icon-check-fill"
              class="text-base text-green-500 dark:text-green-300"
            />
          </div>
        </div>
      </div>
    </template>
  </BaseTable>
  <ScenarioConfigDrawer
    v-if="configDrawerOpen"
    v-model:open="configDrawerOpen"
    :scenario="selectedScenarioSummary!"
    :configuration="campaignConfiguration"
    :scenario-form="selectedScenarioConfigForm"
    :disabled="!rowSelectionEnabled"
    @submit="handleScenarioConfigFormSubmit($event)"
  />
</template>

<script setup lang="ts">
const props = defineProps<{
  campaignConfiguration: DefaultCampaignConfiguration
}>()

const { fetchScenarios } = useScenarioApi()

const toastStore = useToastStore()
const scenarioTableStore = useScenarioTableStore()

const {
  selectedRowKeys,
  selectedRows,
  dataSource,
  pageSize,
  scenarioConfig,
  rowSelectionEnabled,
} = storeToRefs(scenarioTableStore)

const { pagedData, totalElements, currentPageIndex, handlePageChange, handleSorterChange } =
  useClientSidePagination(dataSource, pageSize)

let selectedScenarioSummary: ScenarioSummary | undefined

const selectedScenarioConfigForm = ref<ScenarioConfigurationForm>()

const configDrawerOpen = ref(false)

onMounted(async () => {
  try {
    if (rowSelectionEnabled.value) {
      // Fetches all scenarios and sets to the scenario table store.
      const scenarios = await fetchScenarios()
      scenarioTableStore.$patch({
        dataSource: scenarios,
        allScenarios: scenarios,
      })
    } else {
      scenarioTableStore.$patch({
        dataSource: selectedRows.value,
        allScenarios: selectedRows.value,
      })
    }
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
})

onBeforeUnmount(() => {
  scenarioTableStore.$reset()
})

const handleRefreshBtnClick = async () => {
  await scenarioTableStore.refreshScenarios()
}

const disableRow = (record: ScenarioSummary) => {
  if (!props.campaignConfiguration) return false

  return (
    !selectedRowKeys.value.includes(record.name) &&
    selectedRowKeys.value.length >= props.campaignConfiguration.validation.maxScenariosCount
  )
}

const handleSelectionChange = (tableSelection: TableSelection) => {
  scenarioTableStore.$patch({
    selectedRows: [...tableSelection.selectedRows],
    selectedRowKeys: [...tableSelection.selectedRowKeys],
  })
}

const handleConfigureBtnClick = (scenarioSummary: ScenarioSummary) => {
  selectedScenarioSummary = scenarioSummary
  selectedScenarioConfigForm.value = scenarioTableStore.scenarioConfig[scenarioSummary.name]
  configDrawerOpen.value = true
}

const handleScenarioConfigFormSubmit = (form: ScenarioConfigurationForm) => {
  if (!selectedScenarioSummary) return

  const scenario = selectedScenarioSummary;
  scenarioTableStore.$patch({
    scenarioConfig: {
      ...scenarioTableStore.scenarioConfig,
      [scenario.name]: form,
    },
  })
  if (!scenarioTableStore.selectedRows.some((r) => r.name === scenario.name)) {
    scenarioTableStore.selectedRows = [...scenarioTableStore.selectedRows, scenario]
  }

  if (!scenarioTableStore.selectedRowKeys.some((rowKey) => rowKey === scenario.name)) {
    scenarioTableStore.selectedRowKeys = [...scenarioTableStore.selectedRowKeys, scenario.name]
  }
}
</script>
