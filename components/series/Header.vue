<template>
    <BaseHeader>
        <div class="flex items-center full-width space-between">
            <h2>Series</h2>
            <div class="flex items-center">
                <BaseSearch 
                    v-model="seriesSearchQuery" 
                    placeholder="Search series..."
                    size="large"
                    :collapsable="true"
                    @search="handleSearch"
                />
                <BaseButton 
                    class="ml-2"
                    text="Delete all"
                    :disabled="deleteAllBtnDisabled"
                    :btn-style="'stroke'"
                    :icon="'/icons/icon-delete-small.svg'"
                    @click="handleDeleteSelectedSeriesBtnClick" 
                />
                <BaseButton 
                    class="ml-2"
                    text="Add Series" 
                    :btn-style="'stroke'"
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
    </BaseHeader>
</template>

<script setup lang="ts">
const seriesTableStore = useSeriesTableStore();
const seriesSearchQuery = ref('');
const deleteAllBtnDisabled = computed(() => seriesTableStore.selectedRows?.length === 0);
const dataSeriesReferences = computed(() => seriesTableStore.selectedRowKeys);
const deleteModalContent = computed(() => `${seriesTableStore.selectedRows.map(r => r.displayName).join(',')}`)
const modalOpen = ref(false);

const handleCreateSeriesBtnClick = () => {

}

const handleDeleteSelectedSeriesBtnClick = () => {
    modalOpen.value = true;
}

const handleSearch = () => {
    seriesTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(seriesSearchQuery.value),
        currentPageIndex: 0
    });
    seriesTableStore.fetchDataSeriesTableDataSource();
}

</script>
