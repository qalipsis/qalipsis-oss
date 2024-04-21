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
                <div class="cursor-pointer hover:text-primary-500" @click="handleReportNameClick(record as ReportTableData)">
                    <span>{{ record.displayName }}</span>
                </div>
            </template>
        </template>
        <template #actionCell="{ record }">
            <div class="flex items-center invisible group-hover:visible">
                <div 
                    class="flex items-center mr-4 cursor-pointer h-8"
                    :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    @click="handleDownloadBtnClick(record as ReportTableData)"
                >
                    <a-tooltip>
                        <template #title>Download</template>
                        <BaseIcon icon="/icons/icon-document.svg" />
                    </a-tooltip>
                </div>
                <div 
                    class="flex items-center cursor-pointer h-8"
                    :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    @click="handleDeleteBtnClick(record as ReportTableData)"
                >
                    <a-tooltip>
                        <template #title>Delete</template>
                        <BaseIcon icon="/icons/icon-delete-small.svg" />
                    </a-tooltip>
                </div>
            </div>
        </template>
    </BaseTable>
    <ReportsDeleteConfirmationModal
        v-model:open="modalOpen"
        :reportReferences="reportReferences"
        :modalContent="deleteModalContent"
    />
</template>
  
<script setup lang="ts">
import { storeToRefs } from "pinia";

const userStore = useUserStore();
const reportsTableStore = useReportsTableStore();
const { downloadReport } = useReportApi();
const { dataSource, totalElements, currentPageIndex, pageSize } = storeToRefs(reportsTableStore);

const reportReferences = ref<string[]>([]);
const deleteModalContent = ref('');
const modalOpen = ref(false);

onMounted(async () => {
    _fetchTableData();
})

onBeforeUnmount(() => {
    reportsTableStore.$reset();
})

watch(() => userStore.currentTenantReference, async () => {
    reportsTableStore.$reset();
    await reportsTableStore.fetchReportsTableDataSource();
})

const handleSorterChange = (tableSorter: TableSorter | null) => {
  const sort = tableSorter
    ? `${tableSorter.key}:${tableSorter.direction}`
    : '';
  reportsTableStore.$patch({
    sort: sort
  });
  _fetchTableData();
}

const handleSelectionChange = (tableSelection: TableSelection) => {
    reportsTableStore.$patch({
    selectedRows: tableSelection.selectedRows,
    selectedRowKeys: tableSelection.selectedRowKeys
  });
}

const handlePaginationChange = (pageIndex: number) => {
  reportsTableStore.$patch({
    currentPageIndex: pageIndex,
  });
  _fetchTableData();
}

const handleReportNameClick = (reportTableData: ReportTableData) => {
    navigateTo(`/reports/${reportTableData.reference}`)
}

const handleRefreshBtnClick = () => {
    _fetchTableData();
}

const handleDeleteBtnClick = (reportTableData: ReportTableData) => {
    reportReferences.value = [reportTableData.reference];
    deleteModalContent.value = reportTableData.displayName;
    modalOpen.value = true
}

const _fetchTableData = async () => {
    try {
        await reportsTableStore.fetchReportsTableDataSource();
    } catch (error) {
        console.log(error)
        ErrorHelper.handleHttpResponseError(error)
    }
}

const handleDownloadBtnClick = async (reportTableData: ReportTableData) => {
    try {
        await downloadReport(reportTableData.reference);
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
}

</script>
