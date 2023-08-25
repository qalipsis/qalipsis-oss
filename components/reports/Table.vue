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
</template>
  
<script setup lang="ts">
import { storeToRefs } from "pinia";
import { ReportTableData } from "utils/report";

const userStore = useUserStore();
const reportsTableStore = useReportsTableStore();
const { dataSource, totalElements } = storeToRefs(reportsTableStore);

const tableColumnConfigs = ReportHelper.getTableColumnConfigs();
const currentPage = computed(() => reportsTableStore.currentPageNumber);
const selectedRowKeys = computed(() => reportsTableStore.selectedRowKeys);
const rowSelection = reactive({
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (_: string[], selectedRows: ReportTableData[]) => {
    reportsTableStore.$patch({
      selectedRows: selectedRows
    });
  }
})
const pagination = reactive({
    current: currentPage,
    pageSize: PageHelper.defaultPageSize,
    total: totalElements,
    ...TableHelper.sharedPaginationProperties
});

onMounted(async () => {
    try {
        await reportsTableStore.fetchReportTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error)
    }
})

onBeforeUnmount(() => {
    reportsTableStore.$reset();
})

watch(() => userStore.currentTenantReference, async () => {
    reportsTableStore.$reset();
    await reportsTableStore.fetchReportTableDataSource();
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
        await reportsTableStore.fetchReportTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error)
    }
}

const handleReportNameClick = (reportTableData: ReportTableData) => {

}

const handleDeleteBtnClick = (reportTableData: ReportTableData) => {

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