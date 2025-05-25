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
      row-class="group"
      rowKey="key"
      @sorter-change="handleSorterChange"
      @page-change="handlePaginationChange"
      @selectionChange="handleSelectionChange"
      @refresh="handleRefreshBtnClick"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div
            :class="{ 'cursor-pointer hover:text-primary-500': actionsEnabled }"
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
      </template>
      <template #actionCell="{ record }">
        <div
          v-if="actionsEnabled && record.status === 'SCHEDULED'"
          class="cursor-pointer"
        >
          <Popover class="relative">
            <PopoverButton class="outline-none">
              <div class="flex items-center invisible group-hover:visible">
                <BaseIcon
                  icon="qls-icon-menu"
                  class="text-2xl hover:text-primary-500 text-gray-700"
                />
              </div>
            </PopoverButton>
            <PopoverPanel :class="TailwindClassHelper.menuPanelBaseClass">
              <PopoverButton class="outline-none">
                <div :class="TailwindClassHelper.menuWrapperBaseClass">
                  <div
                    :class="TailwindClassHelper.menuItemBaseClass"
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
                <div :class="TailwindClassHelper.menuWrapperBaseClass">
                  <div
                    :class="TailwindClassHelper.menuItemBaseClass"
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
            </PopoverPanel>
          </Popover>
        </div>
      </template>
    </BaseTable>
    <BaseModal
      title="Abort campaign"
      confirmBtnText="Abort"
      v-model:open="campaignAbortModalOpen"
      :closable="true"
      @confirmBtnClick="handleConfirmAbortBtnClick"
    >
      <span>{{ campaignAbortModalContent }}</span>
    </BaseModal>
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

const userStore = useUserStore()
const campaignsTableStore = useCampaignsTableStore()
const toastStore = useToastStore()

const { dataSource, totalElements, pageSize, currentPageIndex } = storeToRefs(campaignsTableStore)
const { fetchCampaignConfig, createCampaign, abortCampaign } = useCampaignApi()

const selectedRowKeys = computed(() => campaignsTableStore.selectedRowKeys)

const campaignAbortModalOpen = ref(false)
const campaignAbortModalContent = ref('')

let selectedCampaignTableData: CampaignTableData

onMounted(() => {
  _fetchTableData()
})

onBeforeUnmount(() => {
  campaignsTableStore.$reset()
})

watch(
  () => userStore.currentTenantReference,
  () => {
    campaignsTableStore.$reset()
    _fetchTableData()
  }
)

const handlePaginationChange = (pageIndex: number) => {
  campaignsTableStore.$patch({
    currentPageIndex: pageIndex,
  })
  _fetchTableData()
}

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

const handleSorterChange = (tableSorter: TableSorter | null) => {
  const sort = tableSorter ? `${tableSorter.key}:${tableSorter.direction}` : ''
  campaignsTableStore.$patch({
    sort: sort,
  })
  _fetchTableData()
}

const handleRefreshBtnClick = () => {
  _fetchTableData()
}

const handleAbortBtnClick = (campaignTableData: CampaignTableData) => {
  selectedCampaignTableData = campaignTableData
  campaignAbortModalContent.value = `Do you want to abort the scheduled campaign "${campaignTableData.name}"?`
  campaignAbortModalOpen.value = true
}

const handleConfirmAbortBtnClick = async () => {
  try {
    await abortCampaign(selectedCampaignTableData.key, true)
    await _fetchTableData()
    campaignAbortModalOpen.value = false
    toastStore.success({
      text: `The scheduled campaign "${selectedCampaignTableData.name}" has been successfully aborted`,
    })
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleRunNowBtnClick = async (campaignTableData: CampaignTableData) => {
  try {
    // Fetches the campaign config
    const campaignConfig = await fetchCampaignConfig(campaignTableData.key)
    // Creates the campaign
    const campaign = await createCampaign(campaignConfig)
    // navigate to the campaign details
    navigateTo(`/campaigns/${campaign.key}`)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

const handleNameClick = (campaignTableData: CampaignTableData) => {
  if (!props.actionsEnabled) return

  const pageLink =
    campaignTableData.status === ExecutionStatusConstant.SCHEDULED
      ? `/campaigns/config/${campaignTableData.key}`
      : `campaigns/${campaignTableData.key}`

  navigateTo(pageLink)
}

const _fetchTableData = async () => {
  try {
    await campaignsTableStore.fetchCampaignsTableDataSource(props.extraQueryParams)
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}
</script>
