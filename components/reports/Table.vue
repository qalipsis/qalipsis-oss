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
                <div class="table-item-link" @click="handleReportNameClick(record)">
                    <span>{{ record.displayName }}</span>
                </div>
            </template>
            <template v-if="column.key === 'actions'">
                <div class="table-action-item-wrapper">
                    <div class="flex items-center cursor-pointer table-action-item" @click="handleDeleteBtnClick(record)">
                        <BaseIcon icon="/icons/icon-delete-small.svg" />
                        <span> Delete </span>
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
import { storeToRefs } from "pinia";

const userStore = useUserStore();
const reportsTableStore = useReportsTableStore();
const { dataSource, totalElements } = storeToRefs(reportsTableStore);

const tableColumnConfigs = ReportHelper.getTableColumnConfigs();
const currentPage = computed(() => reportsTableStore.currentPageNumber);
const selectedRowKeys = computed(() => reportsTableStore.selectedRowKeys);
const rowSelection = reactive({
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (selectedRowKeys: string[], selectedRows: ReportTableData[]) => {
    reportsTableStore.$patch({
      selectedRows: selectedRows,
      selectedRowKeys: selectedRowKeys
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
        ErrorHelper.handleHttpRequestError(error)
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
    _: FilterConfirmProps,
    sorter: SorterResult) => {
    const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
    const sort = TableHelper.getSort(sorter);
    try {
        reportsTableStore.$patch({
            sort: sort,
            currentPageIndex: currentPageIndex
        })
        await reportsTableStore.fetchReportsTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error)
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