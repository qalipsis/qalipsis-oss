<template>
  <BaseTable
    :data-source="dataSource"
    :table-column-configs="ReportsTableConfig.TABLE_COLUMNS"
    :total-elements="totalElements"
    :page-size="pageSize"
    :row-selection-enabled="true"
    :row-all-selection-enabled="true"
    :current-page-index="currentPageIndex"
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
          class="cursor-pointer hover:text-primary-500"
          @click="handleReportNameClick(record as ReportTableData)"
        >
          <span>{{ record.displayName }}</span>
        </div>
      </template>
    </template>
    <template #actionCell="{ record }">
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
          <PopoverButton class="outline-none w-full">
            <div :class="TailwindClassHelper.menuWrapperBaseClass">
              <div
                :class="TailwindClassHelper.menuItemBaseClass"
                @click="handleDownloadBtnClick(record)"
              >
                <BaseIcon
                  icon="qls-icon-document"
                  class="text-xl"
                />
                <span class="pl-2"> Download </span>
              </div>
            </div>
          </PopoverButton>
          <PopoverButton class="outline-none w-full">
            <div :class="TailwindClassHelper.menuWrapperBaseClass">
              <div
                :class="TailwindClassHelper.menuItemBaseClass"
                @click="handleDeleteBtnClick(record as ReportTableData)"
              >
                <BaseIcon
                  icon="qls-icon-delete"
                  class="text-xl"
                />
                <span class="pl-2"> Delete </span>
              </div>
            </div>
          </PopoverButton>
        </PopoverPanel>
      </Popover>
    </template>
  </BaseTable>
  <ReportsDeleteConfirmationModal
    v-model:open="modalOpen"
    :reportReferences="reportReferences"
    :modalContent="deleteModalContent"
  />
</template>

<script setup lang="ts">
import { Popover, PopoverButton, PopoverPanel } from '@headlessui/vue'

const userStore = useUserStore()
const toastStore = useToastStore()
const reportsTableStore = useReportsTableStore()
const { downloadReport } = useReportApi()
const { dataSource, totalElements, currentPageIndex, pageSize } = storeToRefs(reportsTableStore)

const reportReferences = ref<string[]>([])
const deleteModalContent = ref('')
const modalOpen = ref(false)

onMounted(async () => {
  _fetchTableData()
})

onBeforeUnmount(() => {
  reportsTableStore.$reset()
})

const handleSorterChange = (tableSorter: TableSorter | null) => {
  const sort = tableSorter ? `${tableSorter.key}:${tableSorter.direction}` : ''
  reportsTableStore.$patch({
    sort: sort,
  })
  _fetchTableData()
}

const handleSelectionChange = (tableSelection: TableSelection) => {
  reportsTableStore.$patch({
    selectedRows: tableSelection.selectedRows,
    selectedRowKeys: tableSelection.selectedRowKeys,
  })
}

const handlePaginationChange = (pageIndex: number) => {
  reportsTableStore.$patch({
    currentPageIndex: pageIndex,
  })
  _fetchTableData()
}

const handleReportNameClick = (reportTableData: ReportTableData) => {
  navigateTo(`/reports/${reportTableData.reference}`)
}

const handleRefreshBtnClick = () => {
  _fetchTableData()
}

const handleDeleteBtnClick = (reportTableData: ReportTableData) => {
  reportReferences.value = [reportTableData.reference]
  deleteModalContent.value = reportTableData.displayName
  modalOpen.value = true
}

const _fetchTableData = async () => {
  try {
    await reportsTableStore.fetchReportsTableDataSource()
  } catch (error) {
    console.log(error)
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleDownloadBtnClick = async (reportTableData: ReportTableData) => {
  try {
    await downloadReport(reportTableData.reference)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
