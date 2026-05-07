<template>
  <div>
    <BaseTable
      :data-source="dataSource"
      :table-column-configs="CampaignsTableConfig.TABLE_COLUMNS"
      :totalElements="totalElements"
      :pageSize="pageSize"
      :selected-row-keys="selectedRowKeys"
      :currentPageIndex="currentPageIndex"
      :row-all-selection-enabled="true"
      :row-selection-enabled="rowSelectionEnabled"
      :disable-row="disableRow"
      :row-click-enabled="actionsEnabled"
      row-class="group"
      rowKey="key"
      @sorter-change="handleSorterChange"
      @page-change="handlePaginationChange"
      @selectionChange="handleSelectionChange"
      @refresh="handleRefreshBtnClick"
      @row-click="handleNameClick"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <span>{{ record.name }}</span>
        </template>
        <template v-if="column.key === 'startTime'">
          <span>{{ record.startTime }}</span>
        </template>
        <template v-if="column.key === 'result'">
          <BaseTag
            :text="record.statusTag.text"
            :text-css-class="record.statusTag.textCssClass"
            :background-css-class="record.statusTag.backgroundCssClass"
          />
        </template>
      </template>
      <template #actionCell="{ record }">
        <div
          v-if="actionsEnabled && (record.status === 'SCHEDULED' || finishedStatuses.includes(record.status))"
          class="cursor-pointer"
        >
          <Popover class="relative">
            <PopoverButton class="outline-none">
              <div class="flex items-center invisible group-hover:visible">
                <BaseIcon
                  icon="qls-icon-menu"
                  class="text-2xl hover:text-primary-500 text-gray-700 dark:text-gray-200"
                />
              </div>
            </PopoverButton>
            <PopoverPanel :class="TailwindClassConfig.menuPanelBaseClass">
              <template v-if="record.status === 'SCHEDULED'">
                <PopoverButton class="outline-none">
                  <div :class="TailwindClassConfig.menuWrapperBaseClass">
                    <div
                      :class="TailwindClassConfig.menuItemBaseClass"
                      @click="handleRunNowBtnClick(record)"
                    >
                      <BaseIcon
                        icon="qls-icon-time"
                        class="text-xl"
                      />
                      <span class="pl-2"> Run now </span>
                    </div>
                  </div>
                </PopoverButton>
                <PopoverButton class="outline-none">
                  <div :class="TailwindClassConfig.menuWrapperBaseClass">
                    <div
                      :class="TailwindClassConfig.menuItemBaseClass"
                      @click="handleAbortBtnClick(record)"
                    >
                      <BaseIcon
                        icon="qls-icon-delete"
                        class="text-xl"
                      />
                      <span class="pl-2"> Abort </span>
                    </div>
                  </div>
                </PopoverButton>
              </template>
              <template v-if="finishedStatuses.includes(record.status)">
                <PopoverButton class="outline-none">
                  <div :class="TailwindClassConfig.menuWrapperBaseClass">
                    <div
                      :class="TailwindClassConfig.menuItemBaseClass"
                      @click="handleReplayBtnClick(record)"
                    >
                      <BaseIcon
                        icon="qls-icon-play"
                        class="text-xl"
                      />
                      <span class="pl-2"> Replay </span>
                    </div>
                  </div>
                </PopoverButton>
              </template>
            </PopoverPanel>
          </Popover>
        </div>
      </template>
    </BaseTable>
    <CampaignsAbortModal
      v-model:open="campaignAbortModalOpen"
      :campaign-key="selectedCampaignTableData?.key"
      :campaign-name="selectedCampaignTableData?.name"
      @aborted="handleCampaignAborted()"
    ></CampaignsAbortModal>
  </div>
</template>

<script setup lang="ts">
import { Popover, PopoverButton, PopoverPanel } from '@headlessui/vue'

const props = defineProps<{
  actionsEnabled?: boolean
  rowSelectionEnabled?: boolean
  maxSelectedRows?: number
  extraQueryParams?: { [key: string]: string }
}>()

const campaignsTableStore = useCampaignsTableStore()
const toastStore = useToastStore()

const { dataSource, totalElements, pageSize, currentPageIndex } = storeToRefs(campaignsTableStore)
const { fetchCampaignConfiguration, createCampaign, replayCampaign } = useCampaignApi()

const finishedStatuses: ExecutionStatus[] = ['ABORTED', 'FAILED', 'SUCCESSFUL', 'WARNING']

const selectedRowKeys = computed(() => campaignsTableStore.selectedRowKeys)

const campaignAbortModalOpen = ref(false)

let selectedCampaignTableData: CampaignTableData

const { handlePaginationChange, handleSorterChange, refresh } = useTableLifecycle(
  campaignsTableStore,
  () => campaignsTableStore.fetchCampaignsTableDataSource(props.extraQueryParams)
)

const handleSelectionChange = (tableSelection: TableSelection) => {
  campaignsTableStore.$patch({
    selectedRows: tableSelection.selectedRows,
    selectedRowKeys: tableSelection.selectedRowKeys,
  })
}

const disableRow = (campaign: CampaignTableData): boolean => {
  let disabled = false
  const selectableStatuses: ExecutionStatus[] = [
    ExecutionStatusConstant.SUCCESSFUL,
    ExecutionStatusConstant.FAILED,
    ExecutionStatusConstant.ABORTED,
    ExecutionStatusConstant.WARNING,
  ]

  if (campaign.status && !selectableStatuses.includes(campaign.status)) {
    disabled = true
  } else if (props.maxSelectedRows && selectedRowKeys.value) {
    // When the max number of row selection is specified, the row is disabled when it is not yet selected.
    disabled = selectedRowKeys.value.length >= props.maxSelectedRows && !selectedRowKeys.value.includes(campaign.key)
  } else {
    disabled = campaign.disabled ?? false
  }

  return disabled
}

const handleRefreshBtnClick = () => {
  refresh()
}

const handleAbortBtnClick = (campaignTableData: CampaignTableData) => {
  selectedCampaignTableData = campaignTableData
  campaignAbortModalOpen.value = true
}

const handleCampaignAborted = async () => {
  await refresh()
}

const handleRunNowBtnClick = async (campaignTableData: CampaignTableData) => {
  try {
    // Fetches the campaign config
    const campaignConfig = await fetchCampaignConfiguration(campaignTableData.key)
    // Creates the campaign
    const campaign = await createCampaign(campaignConfig)
    // navigate to the campaign details
    navigateTo(`/campaigns/${campaign.key}`)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleReplayBtnClick = async (campaignTableData: CampaignTableData) => {
  try {
    // Replays the campaign
    await replayCampaign(campaignTableData.key)

    // Reloads table data
    await refresh()
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleNameClick = (campaignTableData: CampaignTableData) => {
  if (!props.actionsEnabled) return

  const pageLink =
    campaignTableData.status === ExecutionStatusConstant.SCHEDULED
      ? `/campaigns/config/${campaignTableData.key}`
      : `/campaigns/${campaignTableData.key}`

  navigateTo(pageLink)
}
</script>
