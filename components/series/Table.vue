<template>
  <BaseTable
    :data-source="dataSource"
    :table-column-configs="SeriesTableConfig.TABLE_COLUMNS"
    :totalElements="totalElements"
    :current-page-index="currentPageIndex"
    :page-size="pageSize"
    :disable-row="disableRow"
    :row-selection-enabled="true"
    :row-all-selection-enabled="true"
    :selected-row-keys="selectedRowKeys"
    row-key="reference"
    row-class="group"
    @sorter-change="handleSorterChange"
    @page-change="handlePaginationChange"
    @selectionChange="handleSelectionChange"
    @refresh="handleRefreshBtnClick"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'displayName'">
        <div
          class="flex items-center"
          :class="{
            'cursor-pointer hover:text-primary-500': canClickDataSeriesName,
          }"
          @click="canClickDataSeriesName && handleEditBtnClick(record as DataSeriesTableData)"
        >
          <div
            class="w-2 h-2 rounded-full mr-2"
            :style="{ backgroundColor: `${record.color || 'transparent'}` }"
          ></div>
          <span>{{ record.displayName }}</span>
        </div>
      </template>
      <template v-if="column.key === 'sharingMode'">
        <span>{{ record.sharedText }}</span>
      </template>
    </template>
    <template #actionCell="{ record }">
      <div
        v-if="!tableActionsHidden"
        class="cursor-pointer"
      >
        <BasePermission :permissions="[PermissionConstant.WRITE_SERIES]">
          <Popover class="relative">
            <PopoverButton class="outline-none">
              <div class="flex items-center invisible group-hover:visible">
                <BaseIcon
                  icon="qls-icon-menu"
                  class="text-2xl hover:text-primary-500 text-gray-700"
                />
              </div>
            </PopoverButton>
            <PopoverPanel :class="TailwindClassHelper.menuPanelBaseClass">
              <div
                v-if="!record.disabled"
                :class="TailwindClassHelper.menuWrapperBaseClass"
              >
                <div
                  :class="TailwindClassHelper.menuItemBaseClass"
                  @click="handleDeleteBtnClick(record as DataSeriesTableData)"
                >
                  <BaseIcon
                    icon="qls-icon-delete"
                    class="text-xl"
                  />
                  <span class="pl-2"> Delete </span>
                </div>
              </div>
              <div
                v-if="!record.disabled"
                :class="TailwindClassHelper.menuWrapperBaseClass"
              >
                <div
                  :class="TailwindClassHelper.menuItemBaseClass"
                  @click="handleEditBtnClick(record as DataSeriesTableData)"
                >
                  <BaseIcon
                    icon="qls-icon-edit"
                    class="text-xl"
                  />
                  <span class="pl-2"> Edit </span>
                </div>
              </div>
              <div :class="TailwindClassHelper.menuWrapperBaseClass">
                <div
                  :class="TailwindClassHelper.menuItemBaseClass"
                  @click="handleDuplicateBtnClick(record as DataSeriesTableData)"
                >
                  <BaseIcon
                    icon="qls-icon-duplicate"
                    class="text-xl"
                  />
                  <span class="pl-2"> Duplicate </span>
                </div>
              </div>
            </PopoverPanel>
          </Popover>
        </BasePermission>
      </div>
    </template>
  </BaseTable>
  <SeriesDeleteConfirmationModal
    v-model:open="modalOpen"
    :dataSeriesReferences="dataSeriesReferences"
    :modalContent="deleteModalContent"
  />
  <SeriesFormDrawer
    v-if="formDrawerOpen"
    v-model:open="formDrawerOpen"
    :data-series="selectedDataSeries"
    @dataSeriesUpdated="seriesTableStore.fetchDataSeriesTableDataSource()"
  />
</template>

<script setup lang="ts">
import { Popover, PopoverButton, PopoverPanel } from '@headlessui/vue'

const props = defineProps<{
  tableActionsHidden?: boolean
  maxSelectedRows?: number
  selectedDataSeriesReferences?: string[]
}>()

const userStore = useUserStore()
const seriesTableStore = useSeriesTableStore()
const toastStore = useToastStore()

const { dataSource, totalElements, currentPageIndex, pageSize } = storeToRefs(seriesTableStore)

const selectedRowKeys = computed(() => seriesTableStore.selectedRowKeys)

const canClickDataSeriesName = computed(() => {
  return userStore.permissions.includes(PermissionConstant.WRITE_SERIES) && !props.tableActionsHidden
})
const selectedDataSeries = ref<DataSeriesTableData>()
const formDrawerOpen = ref(false)
const dataSeriesReferences = ref<string[]>([])
const deleteModalContent = ref('')
const modalOpen = ref(false)

onMounted(() => {
  if (props.selectedDataSeriesReferences) {
    seriesTableStore.$patch({
      selectedRowKeys: props.selectedDataSeriesReferences,
    })
  }
  _fetchTableData()
})

onBeforeUnmount(() => {
  seriesTableStore.$reset()
})

const disableRow = (dataSeriesTableData: DataSeriesTableData): boolean => {
  /**
   * Disable the row select when
   * 1. The max number of row selection is specified and the selected row is more than the max number.
   * 2. The row is disabled
   */
  let disabled = false

  if (props.maxSelectedRows) {
    // When the max number of row selection is specified, the row is disabled when it is not yet selected.
    disabled =
      selectedRowKeys.value.length >= props.maxSelectedRows &&
      !selectedRowKeys.value.includes(dataSeriesTableData.reference)
  } else {
    disabled = dataSeriesTableData.disabled
  }

  return disabled
}

const handleSorterChange = (tableSorter: TableSorter | null) => {
  const sort = tableSorter ? `${tableSorter.key}:${tableSorter.direction}` : ''
  seriesTableStore.$patch({
    sort: sort,
  })
  _fetchTableData()
}

const handlePaginationChange = (pageIndex: number) => {
  seriesTableStore.$patch({
    currentPageIndex: pageIndex,
  })
  _fetchTableData()
}

const handleSelectionChange = (tableSelection: TableSelection) => {
  seriesTableStore.$patch({
    selectedRows: tableSelection.selectedRows,
    selectedRowKeys: tableSelection.selectedRowKeys,
  })
}

const handleEditBtnClick = (dataSeriesTableData: DataSeriesTableData) => {
  formDrawerOpen.value = true
  selectedDataSeries.value = dataSeriesTableData
}

const handleRefreshBtnClick = () => {
  _fetchTableData()
}

const handleDuplicateBtnClick = async (dataSeriesTableData: DataSeriesTableData) => {
  try {
    const { duplicateDataSeries } = useDataSeriesApi()
    await duplicateDataSeries(dataSeriesTableData)
    toastStore.success({ text: `The data series ${dataSeriesTableData.displayName} has been successfully copied` })
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
  _fetchTableData()
}

const handleDeleteBtnClick = (dataSeriesTableData: DataSeriesTableData) => {
  dataSeriesReferences.value = [dataSeriesTableData.key]
  deleteModalContent.value = dataSeriesTableData.displayName
  modalOpen.value = true
}

const _fetchTableData = async () => {
  try {
    await seriesTableStore.fetchDataSeriesTableDataSource()
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
