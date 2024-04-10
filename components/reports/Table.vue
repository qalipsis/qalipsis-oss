<template>
    <a-table 
        :data-source="dataSource"
        :columns="tableColumns"
        :show-sorter-tooltip="false"
        :ellipsis="true"
        rowKey="reference"
        :pagination="pagination"
        :rowSelection="rowSelection"
        @change="handlePaginationChange">
        <template #headerCell="{ column }">
            <template v-if="column.key === 'actions'">
                <div class="flex items-center cursor-pointer" @click="handleRefreshBtnClick()">
                <a-tooltip>
                    <template #title>Refresh</template>
                    <BaseIcon 
                        icon="/icons/icon-refresh.svg"
                        :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    />
                </a-tooltip>
                </div>
            </template>
        </template>
        <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'displayName'">
                <div class="cursor-pointer hover:text-primary-500" @click="handleReportNameClick(record as ReportTableData)">
                    <span>{{ record.displayName }}</span>
                </div>
            </template>
            <template v-if="column.key === 'actions'">
                <div class="table-action-item-wrapper">
                    <div class="flex items-center">
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
                </div>
            </template>
        </template>
    </a-table>
    <ReportsDeleteConfirmationModal
        v-model:open="modalOpen"
        :reportReferences="reportReferences"
        :modalContent="deleteModalContent"
    />
</template>
  
<script setup lang="ts">
import type { TablePaginationConfig } from "ant-design-vue/es/table/Table";
import type { FilterValue, Key, SorterResult, TableRowSelection } from "ant-design-vue/es/table/interface";
import { storeToRefs } from "pinia";

const userStore = useUserStore();
const reportsTableStore = useReportsTableStore();
const { downloadReport } = useReportApi();
const { dataSource, totalElements } = storeToRefs(reportsTableStore);

const tableColumns = ReportsTableConfig.TABLE_COLUMNS;
const currentPage = computed(() => reportsTableStore.currentPageNumber);
const selectedRowKeys = computed(() => reportsTableStore.selectedRowKeys);
const rowSelection: TableRowSelection<ReportTableData> | undefined = reactive({
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (selectedRowKeys: Key[], selectedRows: ReportTableData[]) => {
    reportsTableStore.$patch({
      selectedRows: selectedRows,
      selectedRowKeys: selectedRowKeys as string[]
    });
  }
})
const pagination = reactive({
    current: currentPage,
    pageSize: reportsTableStore.pageSize,
    total: totalElements,
    ...TableHelper.sharedPaginationProperties
});
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

const handlePaginationChange = async (
    pagination: TablePaginationConfig,
    _: Record<string, FilterValue>,
    sorter: SorterResult<any> | SorterResult<any>[]) => {
    const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
    const sort = TableHelper.getSort(sorter as SorterResult<any>);
    reportsTableStore.$patch({
        sort: sort,
        currentPageIndex: currentPageIndex
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
