<template>
    <a-table 
        :data-source="dataSource"
        :columns="tableColumnConfigs"
        :rowSelection="rowSelection"
        :show-sorter-tooltip="false"
        :ellipsis="true"
        :pagination="pagination"
        @change="handlePaginationChange">
        <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
                <div :class="{'cursor-pointer table-item-link': nameClickable }" class="flex items-center" @click="handleNameClick(record)">
                    <span>{{ record.name }}</span>
                </div>
            </template>
            <template v-if="column.key === 'creation'">
                <span>{{ record.creationTime }}</span>
            </template>
            <template v-if="column.key === 'result'">
                <BaseTag
                    :text="record.statusTag.text"
                    :text-css-class="record.statusTag.textCssClass"
                    :background-css-class="record.statusTag.backgroundCssClass"
                />
            </template>
        </template>
    </a-table>
</template>
  
<script setup lang="ts">
import { storeToRefs } from "pinia";

const props = defineProps<{
    nameClickable?: boolean,
    rowSelectionEnabled?: boolean,
    maxSelectedRows?: number
}>()

const userStore = useUserStore();
const campaignsTableStore = useCampaignsTableStore();
const { dataSource, totalElements } = storeToRefs(campaignsTableStore);

const tableColumnConfigs = CampaignHelper.getTableColumnConfigs();
const currentPage = computed(() => campaignsTableStore.currentPageNumber);
const selectedRowKeys = computed(() => campaignsTableStore.selectedRowKeys);
const pagination = reactive({
    current: currentPage,
    pageSize: campaignsTableStore.pageSize,
    total: totalElements,
    ...TableHelper.sharedPaginationProperties
});
const rowSelection = props.rowSelectionEnabled ? reactive({
    // Hides the selected all button when the max number of row selection is specified
    hideSelectAll: props.maxSelectedRows,
    preserveSelectedRowKeys: true,
    selectedRowKeys: selectedRowKeys,
    onChange: (selectedRowKeys: string[], selectedRows: CampaignTableData[]) => {
        campaignsTableStore.$patch({
            selectedRowKeys: selectedRowKeys,
            selectedRows: selectedRows
        });
    },
    getCheckboxProps: (record: CampaignTableData) => {
        let disabled = false;
        if (record?.status === ExecutionStatus.SCHEDULED) {
            disabled = true
        } else if (props.maxSelectedRows) {
            // When the max number of row selection is specified, the row is disabled when it is not yet selected.
            disabled = selectedRowKeys.value.length >= props.maxSelectedRows && !selectedRowKeys.value.includes(record?.key);
        } else {
            disabled = record?.disabled
        }

        return {
            disabled: disabled
        }
    },
}) : null;

onMounted(async () => {
    try {
        await campaignsTableStore.fetchCampaignsTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error)
    }
})

onBeforeUnmount(() => {
    campaignsTableStore.$reset();
})

watch(() => userStore.currentTenantReference, async () => {
    campaignsTableStore.$reset();
    await campaignsTableStore.fetchCampaignsTableDataSource();
})

const handlePaginationChange = async (
    pagination: TablePaginationConfig,
    _: FilterConfirmProps,
    sorter: SorterResult) => {
    const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
    const sort = TableHelper.getSort(sorter);
    try {
        campaignsTableStore.$patch({
            sort: sort,
            currentPageIndex: currentPageIndex
        })
        await campaignsTableStore.fetchCampaignsTableDataSource();
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error)
    }
}

const handleNameClick = (campaignTableData: CampaignTableData) => {
    if (!props.nameClickable) return;

    navigateTo(`campaigns/${campaignTableData.key}`);
}

</script>
  
