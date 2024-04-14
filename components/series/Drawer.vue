<template>
    <BaseDrawer 
        title="Series"
        :open="open"
        :width="920"
        @close="emit('update:open', false)"
        @confirm-btn-click="handleConfirmButtonClick"
    >
        <div class="flex justify-end w-full mt-2 mb-2">
            <BaseSearch 
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

const handleConfirmButtonClick = () => {
    emit("selectedDataSeriesChange", seriesTableStore.selectedRowKeys);
    emit("update:open", false);
}

const handleSearch = (searchTerm: string) => {
    seriesTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(searchTerm),
        currentPageIndex: 0
    });
    // Fetches the table data without minions count
    seriesTableStore.fetchDataSeriesTableDataSource();
}
</script>
