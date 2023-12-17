<template>
    <a-table 
        :data-source="dataSource"
        :columns="tableColumnConfigs"
        :show-sorter-tooltip="false"
        :ellipsis="true"
        rowKey="reference"
        :pagination="pagination"
        :rowSelection="rowSelection"
        @change="handlePaginationChange">
        <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'displayName'">
                <div class="table-item-link" @click="handleReportNameClick(record as ReportTableData)">
                    <span>{{ record.displayName }}</span>
                </div>
            </template>
            <template v-if="column.key === 'actions'">
                <div class="table-action-item-wrapper">
                    <div class="flex items-center">
                        <div class="flex items-center mr-4 cursor-pointer table-action-item" @click="handleDownloadBtnClick(record as ReportTableData)">
                            <a-tooltip>
                                <template #title>Download</template>
                                <BaseIcon icon="/icons/icon-document.svg" />
                            </a-tooltip>
                        </div>
                        <div class="flex items-center cursor-pointer table-action-item" @click="handleDeleteBtnClick(record as ReportTableData)">
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
import { TablePaginationConfig } from "ant-design-vue/es/table/Table";
import { FilterValue, Key, SorterResult, TableRowSelection } from "ant-design-vue/es/table/interface";
import { storeToRefs } from "pinia";

const userStore = useUserStore();
const reportsTableStore = useReportsTableStore();
const { downloadReport } = useReportApi();
const { dataSource, totalElements } = storeToRefs(reportsTableStore);

const tableColumnConfigs = ReportHelper.getTableColumnConfigs();
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
    try {
        await reportsTableStore.fetchReportsTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
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
    try {
        reportsTableStore.$patch({
            sort: sort,
            currentPageIndex: currentPageIndex
        })
        await reportsTableStore.fetchReportsTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
}

const handleReportNameClick = (reportTableData: ReportTableData) => {
    navigateTo(`/reports/${reportTableData.reference}`)
}

const handleDeleteBtnClick = (reportTableData: ReportTableData) => {
    reportReferences.value = [reportTableData.reference];
    deleteModalContent.value = reportTableData.displayName;
    modalOpen.value = true
}

const handleDownloadBtnClick = async (reportTableData: ReportTableData) => {
    try {
        await downloadReport(reportTableData.reference);
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
}

</script>

<style scoped lang="scss">
@import "../../assets/scss/color";

.table-action-item {

    &:hover {
        span {
            color: $primary-color;
        }

        img {
            filter: $primary-color-svg
        }
    }
}
</style>