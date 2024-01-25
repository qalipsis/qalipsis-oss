<template>
  <div>
    <a-table
      :data-source="dataSource"
      :columns="tableColumns"
      :rowSelection="rowSelection"
      :show-sorter-tooltip="false"
      :ellipsis="true"
      :pagination="pagination"
      @change="handlePaginationChange"
    >
      <template #headerCell="{ column }">
        <template v-if="column.key === 'actions'">
          <div class="flex items-center cursor-pointer" @click="handleRefreshBtnClick()">
            <a-tooltip>
              <template #title>Refresh</template>
              <img class="icon-refresh" src="/icons/icon-refresh.svg"  alt="refresh-icon">
            </a-tooltip>
          </div>
        </template>
      </template>
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
          <a-dropdown trigger="click">
            <a @click.prevent class="table-action-item-wrapper">
              <div class="flex items-center">
                <BaseIcon icon="/icons/icon-menu.svg" />
              </div>
            </a>
            <template #overlay>
              <a-menu>
                <a-menu-item>
                  <div
                    class="flex items-center cursor-pointer table-action-item"
                    @click="handleRunNowBtnClick(record as CampaignTableData)"
                  >
                    <BaseIcon icon="/icons/icon-time.svg" />
                    <span> Run now </span>
                  </div>
                </a-menu-item>
                <a-menu-item>
                  <div 
                    class="flex items-center table-action-item" 
                    @click="handleAbortBtnClick(record as CampaignTableData)">
                    <BaseIcon icon="/icons/icon-delete-small.svg" />
                    <span> Abort </span>
                  </div>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </template>
      </template>
    </a-table>
    <BaseModal 
      title="Abort campaign"
      confirmBtnText="Abort"
      v-model:open="campaignAbortModalOpen"
      :closable="true"
      @confirmBtnClick="handleConfirmAbortBtnClick">
      <span>{{ campaignAbortModalContent }}</span>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from "pinia";
import { TableRowSelection, SorterResult, FilterValue, Key } from "ant-design-vue/es/table/interface";
import { TablePaginationConfig } from "ant-design-vue/es/table/Table";

const props = defineProps<{
  actionsEnabled?: boolean;
  rowSelectionEnabled?: boolean;
  maxSelectedRows?: number;
  extraQueryParams?: { [key: string]: string }
}>();

const userStore = useUserStore();
const campaignsTableStore = useCampaignsTableStore();
const { dataSource, totalElements } = storeToRefs(campaignsTableStore);
const { fetchCampaignConfig, createCampaign, abortCampaign } = useCampaignApi();

const tableColumns = CampaignsTableConfig.TABLE_COLUMNS;
const currentPage = computed(() => campaignsTableStore.currentPageNumber);
const selectedRowKeys = computed(() => campaignsTableStore.selectedRowKeys);
const campaignAbortModalOpen = ref(false);
const campaignAbortModalContent = ref("");
let selectedCampaignTableData: CampaignTableData;
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

onMounted(() => {
  _fetchTableData();
});

onBeforeUnmount(() => {
  campaignsTableStore.$reset();
});

watch(
  () => userStore.currentTenantReference,
  () => {
    campaignsTableStore.$reset();
    _fetchTableData();
  }
);

const handlePaginationChange = async (
  pagination: TablePaginationConfig,
  _: Record<string, FilterValue>,
  sorter: SorterResult<any> | SorterResult<any>[]
) => {
  const currentPageIndex = TableHelper.getCurrentPageIndex(pagination);
  const sort = TableHelper.getSort(sorter as SorterResult<any>);
  campaignsTableStore.$patch({
    sort: sort,
    currentPageIndex: currentPageIndex,
  });
  _fetchTableData();
};

const handleRefreshBtnClick = () => {
  _fetchTableData();
}

const handleAbortBtnClick = (campaignTableData: CampaignTableData) => {
  selectedCampaignTableData = campaignTableData;
  campaignAbortModalContent.value = `Do you want to abort the scheduled campaign "${campaignTableData.name}"?`;
  campaignAbortModalOpen.value = true;
}

const handleConfirmAbortBtnClick = async () => {
  try {
    await abortCampaign(selectedCampaignTableData.key, true);
    await _fetchTableData();
    campaignAbortModalOpen.value = false;
    NotificationHelper.success(`The scheduled campaign "${selectedCampaignTableData.name}" has been successfully aborted`);
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
}

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

const _fetchTableData = async () => {
  try {
    await campaignsTableStore.fetchCampaignsTableDataSource(props.extraQueryParams);
  } catch (error) {
    ErrorHelper.handleHttpResponseError(error);
  }
}

</script>
