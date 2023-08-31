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
      <template v-if="column.key === 'displayName'">
        <div class="flex items-center cursor-pointer table-item-link" @click="handleEditBtnClick(record)">
          <div class="dot" :style="{ backgroundColor: `${record.color || 'transparent'}` }"></div>
          <span>{{ record.displayName }}</span>
        </div>
      </template>
      <template v-if="column.key === 'sharingMode'">
        <span>{{ record.sharedText }}</span>
      </template>
      <template v-if="column.key === 'actions' && !tableActionsHidden">
        <BasePermission :permissions="[PermissionEnum.WRITE_SERIES]">
          <a-dropdown trigger="click">
            <a @click.prevent class="table-action-item-wrapper">
              <div class="flex items-center">
                <BaseIcon icon="/icons/icon-menu.svg" />
              </div>
            </a>
            <template #overlay>
              <a-menu>
                <a-menu-item v-if="!record.disabled">
                  <div class="flex items-center table-action-item" @click="handleDeleteBtnClick(record)">
                    <BaseIcon icon="/icons/icon-delete-small.svg" />
                    <span> Delete </span>
                  </div>
                </a-menu-item>
                <a-menu-item v-if="!record.disabled">
                  <div class="flex items-center table-action-item" @click="handleEditBtnClick(record)">
                    <BaseIcon icon="/icons/icon-edit-small.svg" />
                    <span> Edit </span>
                  </div>
                </a-menu-item>
                <a-menu-item>
                  <div class="flex items-center table-action-item"  @click="handleDuplicateBtnClick(record)">
                    <BaseIcon icon="/icons/icon-duplicate.svg" />
                    <span> Duplicate </span>
                  </div>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </BasePermission>
      </template>
    </template>
  </a-table>
  <SeriesDeleteConfirmationModal
    v-model:open="modalOpen"
    :dataSeriesReferences="dataSeriesReferences"
    :modalContent="deleteModalContent"
  />
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { DataSeriesTableData } from "utils/series";

const tableColumnConfigs = SeriesHelper.getTableColumnConfigs();

const props = defineProps<{
  tableActionsHidden?: boolean,
  maxSelectedRows?: number,
  selectedDataSeriesReferences?: string[]
}>()

const seriesTableStore = useSeriesTableStore();
const { dataSource, totalElements } = storeToRefs(seriesTableStore);
const userStore = useUserStore();

const currentPage = computed(() => seriesTableStore.currentPageNumber);
const selectedRowKeys = computed(() => seriesTableStore.selectedRowKeys);
const dataSeriesReferences = ref<string[]>([]);
const deleteModalContent = ref('');
const modalOpen = ref(false);

const pagination = reactive({
  current: currentPage,
  pageSize: seriesTableStore.pageSize,
  total: totalElements,
  ...TableHelper.sharedPaginationProperties
})

const rowSelection = reactive({
  // Hides the selected all button when the max number of row selection is specified
  hideSelectAll: props.maxSelectedRows,
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (selectedRowKeys: string[], selectedRows: DataSeriesTableData[]) => {
    seriesTableStore.$patch({
      selectedRows: selectedRows,
      selectedRowKeys: selectedRowKeys
    });
  },
  getCheckboxProps: (record: DataSeriesTableData) => {
    /**
     * Disable the row select when
     * 1. The data series is minion count
     * 2. The max number of row selection is specified and the selected row is more than the max number.
     * 3. The row is disabled
     */
    const isMinionCount = record.reference === SeriesHelper.MINIONS_COUNT_DATA_SERIES_REFERENCE;
    let disabled = false;
    if (isMinionCount) {
      disabled = true
    } else if (props.maxSelectedRows) {
      // When the max number of row selection is specified, the row is disabled when it is not yet selected.
      disabled = selectedRowKeys.value.length > props.maxSelectedRows && !selectedRowKeys.value.includes(record.reference);
    } else {
      disabled = record.disabled
    }
    return {
      disabled: disabled
    }
  },
})

onMounted(async () => {
  try {
    if (props.selectedDataSeriesReferences) {
      seriesTableStore.$patch({
        selectedRowKeys: props.selectedDataSeriesReferences
      })
    }

    await seriesTableStore.fetchDataSeriesTableDataSource();
  } catch (error) {
    ErrorHelper.handleHttpRequestError(error)
  }
})

onBeforeUnmount(() => {
  seriesTableStore.$reset();
})

watch(() => userStore.currentTenantReference, async () => {
  seriesTableStore.$reset();
  await seriesTableStore.fetchDataSeriesTableDataSource();
})

const handlePaginationChange = async (
  pagination: TablePaginationConfig,
  _: FilterConfirmProps,
  sorter: SorterResult) => {
    const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
    const sort = TableHelper.getSort(sorter);
    try {
      seriesTableStore.$patch({
        sort: sort,
        currentPageIndex: currentPageIndex
      })
      await seriesTableStore.fetchDataSeriesTableDataSource();
    } catch (error) {
      ErrorHelper.handleHttpRequestError(error)
    }
}

// TODO:
const handleEditBtnClick = (dataSeriesTableData: DataSeriesTableData) => {

}

const handleDuplicateBtnClick = async (dataSeriesTableData: DataSeriesTableData) => {
  try {
    const { duplicateDataSeries } = useDataSeriesApi();
    await duplicateDataSeries(dataSeriesTableData);
    NotificationHelper.success(`The data series ${dataSeriesTableData.displayName} has been successfully copied`)  
  } catch (error) {
    ErrorHelper.handleHttpRequestError(error)
  }
  await seriesTableStore.fetchDataSeriesTableDataSource();
}

const handleDeleteBtnClick = (dataSeriesTableData: DataSeriesTableData) => {
  dataSeriesReferences.value = [dataSeriesTableData.key];
  deleteModalContent.value = dataSeriesTableData.displayName;
  modalOpen.value = true
}

</script>

<style scoped lang="scss">
.dot {
  width: .5rem;
  height: .5rem;
  border-radius: 50%;
  margin-right: .5rem
}
</style>