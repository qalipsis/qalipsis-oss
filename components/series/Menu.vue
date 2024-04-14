<template>
    <section>
        <div class="w-full flex items-center series-search-wrapper mb-2">
            <BaseSearch 
                placeholder="Search series..."
                @search="handleSearch"
            />
        </div>
        <a-row :gutter="[8, 8]" class="mb-1">
            <a-col 
                v-for="dataSeriesOption in availableDataSeriesOptions" :key="dataSeriesOption.reference"
                :sm="{ span: 24 }"
                :md="{ span: 12}"
                :lg="{span: 8}"
                :xl="{span: 6}"
                :xxl="{ span: 4 }"
            >
                <SeriesOption            
                    :reference="dataSeriesOption.reference"
                    :displayName="dataSeriesOption.displayName"
                    :dataType="dataSeriesOption.dataType"
                    :isActive="dataSeriesOption.isActive"
                    :color="dataSeriesOption.color"
                    @click="handleDataSeriesOptionClick(dataSeriesOption)"
                />
            </a-col>
            <a-col v-if="showMore"
                :sm="{ span: 24 }"
                :md="{ span: 12}"
                :lg="{span: 8}"
                :xl="{span: 6}"
                :xxl="{ span: 4 }"
            >
                <BaseButton 
                    text="Select more series" 
                    btn-style="outlined"
                    :icon="'/icons/icon-plus-grey.svg'"
                    @click="handleShowMoreBtnClick" 
                />
            </a-col>
        </a-row>
        <SeriesDrawer
            v-if="seriesDrawerOpen"
            v-model:open="seriesDrawerOpen"
            :selectedDataSeriesReferences="selectedDataSeriesReferences"
            @selectedDataSeriesChange="handleSelectedDataSeriesChange"
        />
    </section>
</template>

<script setup lang="ts">

const props = defineProps<{
    preselectedDataSeriesReferences: string[]
}>()
const emit = defineEmits<{
    (e: "selectedDataSeriesChange", v: DataSeriesOption[]): void
}>()

const { fetchAllDataSeries } = useDataSeriesApi();

/**
 * All data series options
 */
const dataSeriesOptions = ref<DataSeriesOption[]>([]);
/**
 * The data series options which are currently displayed.
 */
const availableDataSeriesOptions = ref<DataSeriesOption[]>([]);

const showMore = ref(false);
const seriesDrawerOpen = ref(false);
const selectedDataSeriesReferences = ref<string[]>([]);

onMounted(async () => {
    try {
        // Inits all data series options
        const allDataSeries = (await fetchAllDataSeries()).filter(ds => ds.reference !== SeriesDetailsConfig.MINIONS_COUNT_DATA_SERIES_REFERENCE);
        dataSeriesOptions.value = SeriesHelper.toDataSeriesOptions(allDataSeries, props.preselectedDataSeriesReferences);
        availableDataSeriesOptions.value = dataSeriesOptions.value.slice(0, SeriesDetailsConfig.MAX_DATA_SERIES_TO_BE_DISPLAYED)
            .map(dataSeriesOption => Object.assign({}, dataSeriesOption));
        showMore.value = dataSeriesOptions.value.length > availableDataSeriesOptions.value.length;
    } catch (error) {
        ErrorHelper.handleHttpResponseError(error)
    }
})

const handleDataSeriesOptionClick = (dataSeriesOption: DataSeriesOption) => {
    // Updates the current data series option active state
    dataSeriesOption.isActive = !dataSeriesOption.isActive;
    // Updates the data series option from all data series options active state
    const selectedOption = dataSeriesOptions.value.find(d => d.reference === dataSeriesOption.reference);
    selectedOption!.isActive = !selectedOption!.isActive;

    // Emits all active data series options
    emit('selectedDataSeriesChange', dataSeriesOptions.value.filter(d => d.isActive));
}

const handleSearch = (searchTerm: string) => {
    const searchKeys = ["reference", "displayName", "dataType", "valueName"]
    const searchedResult = SearchHelper.performFuzzySearch<DataSeriesOption>(searchTerm, dataSeriesOptions.value, searchKeys);
    availableDataSeriesOptions.value = SeriesHelper.toDataSeriesOptions(searchedResult.slice(0, SeriesDetailsConfig.MAX_DATA_SERIES_TO_BE_DISPLAYED + 1), props.preselectedDataSeriesReferences);
}

const handleShowMoreBtnClick = () => {
    selectedDataSeriesReferences.value = dataSeriesOptions.value.filter(d => d.isActive).map(d => d.reference);
    seriesDrawerOpen.value = true;
}

const handleSelectedDataSeriesChange = (selectedDataSeriesReferences: string[]) => {
    // Updates the active state for all series options
    dataSeriesOptions.value = dataSeriesOptions.value.map(dataSeriesOption => ({
        ...dataSeriesOption,
        isActive: selectedDataSeriesReferences.includes(dataSeriesOption.reference)
    }))
    // Updates the active state for series options
    availableDataSeriesOptions.value = availableDataSeriesOptions.value.map(dataSeriesOption => ({
        ...dataSeriesOption,
        isActive: selectedDataSeriesReferences.includes(dataSeriesOption.reference)
    }))
    // Emits all active data series options
    emit('selectedDataSeriesChange', dataSeriesOptions.value.filter(d => d.isActive));
}

</script>

<style scoped>
.series-search-wrapper {
    justify-content: end;
}
</style>