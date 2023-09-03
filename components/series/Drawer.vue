<template>
    <BaseDrawer 
        title="Series"
        :open="open"
        :width="920"
        @close="emit('update:open', false)"
        @confirm-btn-click="handleConfirmButtonClick"
    >
        <div class="flex content-end full-width mt-2 mb-2">
            <BaseSearch 
                v-model="seriesSearchQuery" 
                placeholder="Search series..."
                :collapsable="true"
                @search="handleSearch"
            />
        </div>
        <SeriesTable
            :table-actions-hidden="true"
            :max-selected-rows="10"
            :selectedDataSeriesReferences="selectedDataSeriesReferences"
        />
    </BaseDrawer>
</template>

<script setup lang="ts">
defineProps<{
    open: boolean;
    selectedDataSeriesReferences: string[]
}>()
const emit = defineEmits<{
    (e: "update:open", v: boolean): void,
    (e: "selectedDataSeriesChange", v: string[]): void
}>()

const seriesTableStore = useSeriesTableStore();

const seriesSearchQuery = ref("");

const handleConfirmButtonClick = () => {
    emit("selectedDataSeriesChange", seriesTableStore.selectedRowKeys);
    emit("update:open", false);
}

const handleSearch = () => {
    seriesTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(seriesSearchQuery.value),
        currentPageIndex: 0
    });
    // Fetches the table data without minions count
    seriesTableStore.fetchDataSeriesTableDataSource(true);
}
</script>
