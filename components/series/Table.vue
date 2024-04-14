<template>
  <a-table 
    :data-source="dataSource"
    :columns="tableColumns"
    :rowSelection="rowSelection"
    :show-sorter-tooltip="false"
    :ellipsis="true"
    :pagination="pagination"
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
        <div class="flex items-center cursor-pointer hover:text-primary-500" @click="handleEditBtnClick(record as DataSeriesTableData)">
          <div class="dot" :style="{ backgroundColor: `${record.color || 'transparent'}` }"></div>
          <span>{{ record.displayName }}</span>
        </div>
      </template>
      <template v-if="column.key === 'sharingMode'">
        <span>{{ record.sharedText }}</span>
      </template>
      <template v-if="column.key === 'actions' && !tableActionsHidden">
        <BasePermission :permissions="[PermissionConstant.WRITE_SERIES]">
          <a-dropdown trigger="click">
            <a @click.prevent class="table-action-item-wrapper">
              <div class="flex items-center">
                <BaseIcon icon="/icons/icon-menu.svg" />
              </div>
            </a>
            <template #overlay>
              <a-menu>
                <a-menu-item v-if="!record.disabled">
                  <div 
                    class="flex items-center h-8"
                    :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    @click="handleDeleteBtnClick(record as DataSeriesTableData)">
                    <BaseIcon icon="/icons/icon-delete-small.svg" />
                    <span class="pl-2"> Delete </span>
                  </div>
                </a-menu-item>
                <a-menu-item v-if="!record.disabled">
                  <div 
                    class="flex items-center h-8"
                    :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    @click="handleEditBtnClick(record as DataSeriesTableData)">
                    <BaseIcon icon="/icons/icon-edit-small.svg" />
                    <span class="pl-2"> Edit </span>
                  </div>
                </a-menu-item>
                <a-menu-item>
                  <div 
                    class="flex items-center h-8"
                    :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    @click="handleDuplicateBtnClick(record as DataSeriesTableData)">
                    <BaseIcon icon="/icons/icon-duplicate.svg" />
                    <span class="pl-2"> Duplicate </span>
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
  <SeriesFormDrawer
    v-if="formDrawerOpen"
    v-model:open="formDrawerOpen"
    :data-series="selectedDataSeries"
    @dataSeriesUpdated="seriesTableStore.fetchDataSeriesTableDataSource()"
  />
</template>

<script setup lang="ts">
import type { TablePaginationConfig } from "ant-design-vue/es/table/Table";
import type { FilterValue, Key, SorterResult, TableRowSelection } from "ant-design-vue/es/table/interface";
import { storeToRefs } from "pinia";

const tableColumns = SeriesTableConfig.TABLE_COLUMNS;

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

const selectedDataSeries = ref<DataSeriesTableData>();
const formDrawerOpen = ref(false);
const dataSeriesReferences = ref<string[]>([]);
const deleteModalContent = ref('');
const modalOpen = ref(false);

const pagination = reactive({
  current: currentPage,
  pageSize: seriesTableStore.pageSize,
  total: totalElements,
  ...TableHelper.sharedPaginationProperties
})

const rowSelection: TableRowSelection<DataSeriesTableData> | undefined = reactive({
  // Hides the selected all button when the max number of row selection is specified
  hideSelectAll: props.maxSelectedRows ? true : false,
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (selectedRowKeys: Key[], selectedRows: DataSeriesTableData[]) => {
    seriesTableStore.$patch({
      selectedRows: selectedRows,
      selectedRowKeys: selectedRowKeys as string[]
    });
  },
  getCheckboxProps: (record: DataSeriesTableData) => {
    /**
     * Disable the row select when
     * 1. The data series is minion count
     * 2. The max number of row selection is specified and the selected row is more than the max number.
     * 3. The row is disabled
     */
    const isMinionCount = record.reference === SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE;
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

onMounted(() => {
  if (props.selectedDataSeriesReferences) {
    seriesTableStore.$patch({
      selectedRowKeys: props.selectedDataSeriesReferences
    })
  }
  _fetchTableData();
})

onBeforeUnmount(() => {
  seriesTableStore.$reset();
})

watch(() => userStore.currentTenantReference, () => {
  seriesTableStore.$reset();
  _fetchTableData();
})

const handlePaginationChange = (
  pagination: TablePaginationConfig,
  _:  Record<string, FilterValue>,
  sorter: SorterResult<any> | SorterResult<any>[]
) => {
  const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
  const sort = TableHelper.getSort(sorter as SorterResult<any>);
  seriesTableStore.$patch({
    sort: sort,
    currentPageIndex: currentPageIndex
  });
  _fetchTableData();
}

const handleEditBtnClick = (dataSeriesTableData: DataSeriesTableData) => {
  formDrawerOpen.value = true;
  selectedDataSeries.value = dataSeriesTableData;
}

const handleRefreshBtnClick = () => {
  _fetchTableData();
}

const handleDuplicateBtnClick = async (dataSeriesTableData: DataSeriesTableData) => {
  try {
    const { duplicateDataSeries } = useDataSeriesApi();
    await duplicateDataSeries(dataSeriesTableData);
    NotificationHelper.success(`The data series ${dataSeriesTableData.displayName} has been successfully copied`)  
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error)
  }
  _fetchTableData();
}

const handleDeleteBtnClick = (dataSeriesTableData: DataSeriesTableData) => {
  dataSeriesReferences.value = [dataSeriesTableData.key];
  deleteModalContent.value = dataSeriesTableData.displayName;
  modalOpen.value = true
}

const _fetchTableData = async () => {
  try {
    await seriesTableStore.fetchDataSeriesTableDataSource();
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
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