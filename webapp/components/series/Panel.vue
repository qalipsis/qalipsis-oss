<template>
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
    <div class="flex flex-col gap-y-2 overflow-y-auto">
      <template
        v-for="dataSeriesOption in displayedOptions"
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
      <p
        v-if="showCapNotice"
        class="text-xs text-gray-400 dark:text-gray-500 text-center px-2 py-1"
      >
        Showing {{ displayedOptions.length }} of {{ dataSeriesOptions.length }} series — search to find more
      </p>
    </div>
    <BaseButton
      v-if="hasMore && !currentSearchTerm"
      text="Show more"
      btn-style="outlined"
      @click="showMoreDrawerOpen = true"
    />
    <SeriesPanelDrawer
      v-if="showMoreDrawerOpen"
      v-model:open="showMoreDrawerOpen"
      :preselectedDataSeriesReferences="activeReferences"
      :campaignKey="campaignKey"
      @selectedDataSeriesChange="handleDrawerSelectionChange"
    />
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  preselectedDataSeriesReferences: string[]
  campaignKey?: string
  maximum?: number
}>()
const emit = defineEmits<{
  (e: 'selectedDataSeriesChange', v: DataSeriesOption[]): void
}>()

const MAX_DATA_SERIES_ITEMS = 100

const toastStore = useToastStore()

const { getCachedDataSeries, fetchDataSeries } = useDataSeriesApi()

const forThisCampaignOnly = ref(true)

/**
 * All data series options, with up-to-date active states.
 */
const dataSeriesOptions = ref<DataSeriesOption[]>([])

/**
 * The data series options currently displayed in the list.
 */
const availableDataSeriesOptions = ref<DataSeriesOption[]>([])

const currentSearchTerm = ref('')
const showMoreDrawerOpen = ref(false)

const displayedOptions = computed(() => {
  const cap = props.maximum ?? MAX_DATA_SERIES_ITEMS
  return availableDataSeriesOptions.value.slice(0, cap)
})

const hasMore = computed(() => !!props.maximum && dataSeriesOptions.value.length > props.maximum)

const showCapNotice = computed(() => displayedOptions.value.length < availableDataSeriesOptions.value.length)

const activeReferences = computed(() => dataSeriesOptions.value.filter((d) => d.isActive).map((d) => d.reference))

/**
 * Rebuilds `availableDataSeriesOptions` from the cached data.
 * Selected items are always sorted to the top.
 */
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
    filter: TableHelper.getSanitizedQuery(searchTerm),
    ...(forThisCampaignOnly.value && props.campaignKey ? { campaign: props.campaignKey } : {}),
  }

  const searchedResult = await fetchDataSeries(request)

  availableDataSeriesOptions.value = SeriesHelper.toDataSeriesOptions(
    searchedResult.elements.slice(0, MAX_DATA_SERIES_ITEMS + 1),
    props.preselectedDataSeriesReferences,
  )
}

const handleDataSeriesOptionClick = (dataSeriesOption: DataSeriesOption) => {
  const selectedOption = dataSeriesOptions.value.find((d) => d.reference === dataSeriesOption.reference)!
  selectedOption.isActive = !selectedOption.isActive

  emit(
    'selectedDataSeriesChange',
    dataSeriesOptions.value.filter((d) => d.isActive),
  )

  if (currentSearchTerm.value) {
    // Search results are not rebuilt — just sync the clicked item's active state
    dataSeriesOption.isActive = selectedOption.isActive
  } else {
    refreshAvailableOptions()
  }
}

const handleDrawerSelectionChange = (selected: DataSeriesOption[]) => {
  const activeSet = new Set(selected.map((d) => d.reference))
  dataSeriesOptions.value.forEach((d) => {
    d.isActive = activeSet.has(d.reference)
  })
  emit(
    'selectedDataSeriesChange',
    dataSeriesOptions.value.filter((d) => d.isActive),
  )
  refreshAvailableOptions()
}
</script>
