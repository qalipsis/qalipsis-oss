<template>
  <a-table
    :data-source="dataSource"
    :columns="tableColumnConfigs"
    :rowSelection="rowSelection"
    :show-sorter-tooltip="false"
    :ellipsis="true"
    :pagination="pagination"
    @change="handlePaginationChange"
  >
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'name'">
        <div
          :class="{ 'cursor-pointer table-item-link': actionsEnabled }"
          class="flex items-center"
          @click="handleNameClick(record as CampaignTableData)"
        >
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
      <template
        v-if="
          column.key === 'actions' &&
          actionsEnabled &&
          record.status === 'SCHEDULED'
        "
      >
        <div class="table-action-item-wrapper">
          <div
            class="flex items-center cursor-pointer table-action-item"
            @click="handleRunNowBtnClick(record as CampaignTableData)"
          >
            <BaseIcon icon="/icons/icon-time.svg" />
            <span> Run now </span>
          </div>
        </div>
      </template>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { TableRowSelection, SorterResult, FilterValue, Key } from "ant-design-vue/es/table/interface";
import { TablePaginationConfig } from "ant-design-vue/es/table/Table";

const props = defineProps<{
  actionsEnabled?: boolean;
  rowSelectionEnabled?: boolean;
  maxSelectedRows?: number;
}>();

const userStore = useUserStore();
const campaignsTableStore = useCampaignsTableStore();
const { dataSource, totalElements } = storeToRefs(campaignsTableStore);
const { fetchCampaignConfig, createCampaign } = useCampaignApi();

const tableColumnConfigs = CampaignHelper.getTableColumnConfigs();
const currentPage = computed(() => campaignsTableStore.currentPageNumber);
const selectedRowKeys = computed(() => campaignsTableStore.selectedRowKeys);
const pagination: TablePaginationConfig = reactive({
  current: currentPage,
  pageSize: campaignsTableStore.pageSize,
  total: totalElements,
  ...TableHelper.sharedPaginationProperties,
});
const rowSelection: TableRowSelection<CampaignTableData> | undefined = props.rowSelectionEnabled
  ? reactive({
      // Hides the selected all button when the max number of row selection is specified
      hideSelectAll: props.maxSelectedRows ? true : false,
      preserveSelectedRowKeys: true,
      selectedRowKeys: selectedRowKeys,
      onChange: (
        selectedRowKeys: Key[],
        selectedRows: CampaignTableData[]
      ) => {
        campaignsTableStore.$patch({
          selectedRowKeys: selectedRowKeys as string[],
          selectedRows: selectedRows,
        });
      },
      getCheckboxProps: (record: CampaignTableData) => {
        let disabled = false;
        const selectableStatuses: ExecutionStatus[] = [
          ExecutionStatusConstant.SUCCESSFUL,
          ExecutionStatusConstant.FAILED,
          ExecutionStatusConstant.ABORTED,
          ExecutionStatusConstant.WARNING,
        ];
        if (record?.status && !selectableStatuses.includes(record.status)) {
          disabled = true;
        } else if (props.maxSelectedRows && selectedRowKeys.value) {
          // When the max number of row selection is specified, the row is disabled when it is not yet selected.
          disabled =
            selectedRowKeys.value.length >= props.maxSelectedRows &&
            !selectedRowKeys.value.includes(record?.key);
        } else {
          disabled = record?.disabled ?? false;
        }

        return {
          disabled: disabled,
        };
      },
    })
  : undefined;

onMounted(async () => {
  try {
    await campaignsTableStore.fetchCampaignsTableDataSource();
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
});

onBeforeUnmount(() => {
  campaignsTableStore.$reset();
});

watch(
  () => userStore.currentTenantReference,
  async () => {
    campaignsTableStore.$reset();
    await campaignsTableStore.fetchCampaignsTableDataSource();
  }
);

const handlePaginationChange = async (
  pagination: TablePaginationConfig,
  _: Record<string, FilterValue>,
  sorter: SorterResult<any> | SorterResult<any>[]
) => {
  const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
  const sort = TableHelper.getSort(sorter as SorterResult<any>);
  try {
    campaignsTableStore.$patch({
      sort: sort,
      currentPageIndex: currentPageIndex,
    });
    await campaignsTableStore.fetchCampaignsTableDataSource();
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
};

const handleRunNowBtnClick = async (
  campaignTableData: CampaignTableData
) => {
  try {
    // Fetches the campaign config
    const campaignConfig = await fetchCampaignConfig(campaignTableData.key);
    // Creates the campaign
    const campaign = await createCampaign(campaignConfig);
    // navigate to the campaign details
    navigateTo(`/campaigns/${campaign.key}`);
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error)
  }

};

const handleNameClick = (
  campaignTableData: CampaignTableData
) => {
  if (!props.actionsEnabled) return;

  const pageLink = campaignTableData.status === ExecutionStatusConstant.SCHEDULED
    ? `/campaigns/config/${campaignTableData.key}`
    : `campaigns/${campaignTableData.key}`;

  navigateTo(pageLink);
};
</script>
