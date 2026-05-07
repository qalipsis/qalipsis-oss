<template>
  <BaseTable
    :data-source="dataSource"
    :table-column-configs="ReportsTableConfig.TABLE_COLUMNS"
    :total-elements="totalElements"
    :page-size="pageSize"
    :row-selection-enabled="true"
    :row-all-selection-enabled="true"
    :current-page-index="currentPageIndex"
    :row-click-enabled="true"
    row-key="reference"
    row-class="group"
    @sorter-change="handleSorterChange"
    @page-change="handlePaginationChange"
    @selectionChange="handleSelectionChange"
    @refresh="handleRefreshBtnClick"
    @row-click="handleReportNameClick"
  >
    <template #actionCell="{ record }">
      <Popover class="relative">
        <PopoverButton class="outline-none">
          <div class="flex items-center invisible group-hover:visible">
            <BaseIcon
              icon="qls-icon-menu"
              class="text-2xl hover:text-primary-500 text-gray-700 dark:text-gray-200"
            />
          </div>
        </PopoverButton>
        <PopoverPanel :class="TailwindClassConfig.menuPanelBaseClass">
          <PopoverButton class="outline-none w-full">
            <div :class="TailwindClassConfig.menuWrapperBaseClass">
              <div
                :class="TailwindClassConfig.menuItemBaseClass"
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
            <div :class="TailwindClassConfig.menuWrapperBaseClass">
              <div
                :class="TailwindClassConfig.menuItemBaseClass"
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

const { handlePaginationChange, handleSorterChange, refresh } = useTableLifecycle(
  reportsTableStore,
  () => reportsTableStore.fetchReportsTableDataSource()
)

const handleSelectionChange = (tableSelection: TableSelection) => {
  reportsTableStore.$patch({
    selectedRows: tableSelection.selectedRows,
    selectedRowKeys: tableSelection.selectedRowKeys,
  })
}

const handleReportNameClick = (reportTableData: ReportTableData) => {
  navigateTo(`/reports/${reportTableData.reference}`)
}

const handleRefreshBtnClick = () => {
  refresh()
}

const handleDeleteBtnClick = (reportTableData: ReportTableData) => {
  reportReferences.value = [reportTableData.reference]
  deleteModalContent.value = reportTableData.displayName
  modalOpen.value = true
}

const handleDownloadBtnClick = async (reportTableData: ReportTableData) => {
  try {
    await downloadReport(reportTableData.reference)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
