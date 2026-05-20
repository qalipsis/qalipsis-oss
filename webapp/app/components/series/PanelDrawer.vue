<template>
  <BaseDrawer
    :open="open"
    title="All Series"
    :footer-hidden="true"
    @close="emit('update:open', false)"
    @update:open="emit('update:open', $event)"
  >
    <div class="flex flex-col h-full gap-y-2 overflow-y-hidden">
      <BaseSearch
        placeholder="Search series..."
        @search="handleSearch"
      />
      <div class="px-1">
        <label
          v-if="campaignKey"
          class="flex items-center gap-x-2 my-1 cursor-pointer text-sm"
        >
          <input
            type="checkbox"
            :class="[TailwindClassConfig.checkBoxClass, TailwindClassConfig.checkBoxMarkerClass]"
            v-model="forThisCampaignOnly"
          />
          For this campaign only
        </label>
      </div>
      <div class="flex flex-col gap-y-2 overflow-y-auto flex-1">
        <template
          v-for="dataSeriesOption in pagedData"
          :key="dataSeriesOption.reference"
        >
          <SeriesPanelOption
            :reference="dataSeriesOption.reference"
            :displayName="dataSeriesOption.displayName"
            :dataType="dataSeriesOption.dataType"
            :isActive="dataSeriesOption.isActive"
            :color="dataSeriesOption.color"
            @click="handleDataSeriesOptionClick(dataSeriesOption)"
          />
        </template>
      </div>
      <div class="pt-4">
        <BaseTablePaginator
          v-if="totalElements > PAGE_SIZE"
          class="mt-auto"
          :totalElements="totalElements"
          :pageSize="PAGE_SIZE"
          :currentPageIndex="currentPageIndex"
          :maxVisiblePages="3"
          @pageChange="handlePageChange"
        />
      </div>
    </div>
  </BaseDrawer>
</template>

<script setup lang="ts">
const props = defineProps<{
  open: boolean
  preselectedDataSeriesReferences: string[]
  campaignKey?: string
}>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'selectedDataSeriesChange', v: DataSeriesOption[]): void
}>()

const PAGE_SIZE = 50

const toastStore = useToastStore()

const { getCachedDataSeries, fetchDataSeries } = useDataSeriesApi()

const forThisCampaignOnly = ref(true)

const dataSeriesOptions = ref<DataSeriesOption[]>([])
const availableDataSeriesOptions = ref<DataSeriesOption[]>([])
const currentSearchTerm = ref('')

const { pagedData, totalElements, currentPageIndex, handlePageChange } = useClientSidePagination(
  availableDataSeriesOptions,
  PAGE_SIZE,
)

const activeReferences = computed(() => dataSeriesOptions.value.filter((d) => d.isActive).map((d) => d.reference))

const refreshAvailableOptions = () => {
  const selectedItems = dataSeriesOptions.value.filter((d) => d.isActive)
  const unselectedItems = dataSeriesOptions.value.filter((d) => !d.isActive)

  availableDataSeriesOptions.value = [...selectedItems, ...unselectedItems].map((d) => Object.assign({}, d))
}

const loadInitialData = async () => {
  try {
    const params: DataSeriesPageQueryParams = {
      ...(forThisCampaignOnly.value && props.campaignKey ? { campaign: props.campaignKey } : {}),
    }
    const allDataSeries = (await getCachedDataSeries(params)).filter(
      (ds) => ds.reference !== SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE,
    )
    dataSeriesOptions.value = SeriesHelper.toDataSeriesOptions(allDataSeries, props.preselectedDataSeriesReferences)
    refreshAvailableOptions()
  } catch (error) {
    toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
  }
}

onMounted(loadInitialData)

watch(forThisCampaignOnly, loadInitialData)

const handleSearch = async (searchTerm: string) => {
  currentSearchTerm.value = searchTerm

  if (!searchTerm) {
    refreshAvailableOptions()

    return
  }

  const request: DataSeriesPageQueryParams = {
    page: 0,
    size: 100,
    filter: searchTerm,
    ...(forThisCampaignOnly.value && props.campaignKey ? { campaign: props.campaignKey } : {}),
  }

  const searchedResult = await fetchDataSeries(request)

  availableDataSeriesOptions.value = SeriesHelper.toDataSeriesOptions(searchedResult.elements, activeReferences.value)
}

const handleDataSeriesOptionClick = (dataSeriesOption: DataSeriesOption) => {
  const selectedOption = dataSeriesOptions.value.find((d) => d.reference === dataSeriesOption.reference)!
  selectedOption.isActive = !selectedOption.isActive
  // Sync the displayed copy so the visual state updates without rebuilding the list (which would reset the current page).
  dataSeriesOption.isActive = selectedOption.isActive

  emit(
    'selectedDataSeriesChange',
    dataSeriesOptions.value.filter((d) => d.isActive),
  )
}
</script>
