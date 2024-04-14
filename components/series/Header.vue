<template>
    <BaseHeader>
        <div class="flex items-center w-full justify-between">
            <BaseTitle content="Series" />
            <div class="flex items-center">
                <BaseSearch 
                    placeholder="Search series..."
                    size="large"
                    :collapsable="true"
                    @search="handleSearch"
                />
                <BaseButton 
                    class="ml-2"
                    text="Delete all"
                    :disabled="deleteAllBtnDisabled"
                    btn-style="outlined"
                    :icon="'/icons/icon-delete-small.svg'"
                    @click="handleDeleteSelectedSeriesBtnClick" 
                />
                <BaseButton 
                    class="ml-2"
                    text="Add Series" 
                    btn-style="outlined"
                    :icon="'/icons/icon-plus-grey.svg'"
                    @click="handleCreateSeriesBtnClick" 
                />
            </div>
        </div>
        <SeriesDeleteConfirmationModal
            v-model:open="modalOpen"
            :dataSeriesReferences="dataSeriesReferences"
            :modalContent="deleteModalContent"
        />
        <SeriesFormDrawer
            v-if="drawerOpen"
            v-model:open="drawerOpen"
            @data-series-updated="handleDataSeriesUpdated"
        />
    </BaseHeader>
</template>

<script setup lang="ts">
const seriesTableStore = useSeriesTableStore();
const deleteAllBtnDisabled = computed(() => seriesTableStore.selectedRows?.length === 0);
const dataSeriesReferences = computed(() => seriesTableStore.selectedRowKeys);
const deleteModalContent = computed(() => `${seriesTableStore.selectedRows.map(r => r.displayName).join(',')}`)
const modalOpen = ref(false);
const drawerOpen = ref(false);

const handleCreateSeriesBtnClick = () => {
    drawerOpen.value = true
}

const handleDeleteSelectedSeriesBtnClick = () => {
    modalOpen.value = true;
}

const handleDataSeriesUpdated = () => {
    seriesTableStore.fetchDataSeriesTableDataSource();
}

const handleSearch = (searchTerm: string) => {
    seriesTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(searchTerm),
        currentPageIndex: 0
    });
    seriesTableStore.fetchDataSeriesTableDataSource();
}

</script>
