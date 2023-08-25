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
      <template v-if="column.key === 'actions'">
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
    ref="seriesDeleteConfirmationModal"
    :dataSeriesReferences="dataSeriesReferences"
    :modalContent="deleteModalContent"
  />
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";

const seriesTableStore = useSeriesTableStore();
const userStore = useUserStore();

const tableColumnConfigs = SeriesHelper.getTableColumnConfigs();
const { dataSource, totalElements } = storeToRefs(seriesTableStore);
const currentPage = computed(() => seriesTableStore.currentPageNumber);
const selectedRowKeys = computed(() => seriesTableStore.selectedRowKeys);
const seriesDeleteConfirmationModal = ref(null);
const dataSeriesReferences = ref([]);
const deleteModalContent = ref('');

const pagination = reactive({
  current: currentPage,
  pageSize: PageHelper.defaultPageSize,
  total: totalElements,
  ...TableHelper.sharedPaginationProperties
})

const rowSelection = reactive({
  preserveSelectedRowKeys: true,
  selectedRowKeys: selectedRowKeys,
  onChange: (_: string[], selectedRows: DataSeriesTableData[]) => {
    seriesTableStore.$patch({
      selectedRows: selectedRows
    });
  },
  getCheckboxProps: (record: DataSeriesTableData) => {
    return {
      disabled: record.disabled
    }
  },
})

onMounted(async () => {
  try {
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
  seriesDeleteConfirmationModal.value.open();
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