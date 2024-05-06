<template>
    <BaseModal 
        :open="open"
        :title="'Delete series'"
        confirmBtnText="Delete"
        @close="emits('update:open', false)"
        @confirmBtnClick="handleConfirmButtonClick"
        >
        <section>
            <div>Do you want to delete the following data series:</div>
            <span class="text-bold">{{ modalContent }}</span>
        </section>
    </BaseModal>
</template>

<script setup lang="ts">
const props = defineProps<{
    open: boolean;
    modalContent: string;
    dataSeriesReferences: string[];
}>()
const emits = defineEmits<{
    (e: 'update:open', v: boolean): void
}>()
const seriesTableStore = useSeriesTableStore();
const toastStore = useToastStore();

const handleConfirmButtonClick = async () => {
    try {
        const { deleteDataSeries } = useDataSeriesApi();
        await deleteDataSeries(props.dataSeriesReferences);
        emits('update:open', false)
        toastStore.success({ text: `Successfully delete ${props.modalContent}` });
    } catch (error) {
        toastStore.error({ text: ErrorHelper.getErrorMessage(error) });
    }

    // When the selected data reference are greater or equal to the current display data
    let pageIndex = seriesTableStore.currentPageIndex;
    if (props.dataSeriesReferences.length > seriesTableStore.dataSource?.length) {
        // Sets the current page index to the previous one. 
        pageIndex = seriesTableStore.currentPageIndex - 1 >= 0 ? seriesTableStore.currentPageIndex - 1 : 0;
    }

    seriesTableStore.$patch({
        currentPageIndex: pageIndex,
        selectedRows: [],
        selectedRowKeys: []
    })

    seriesTableStore.fetchDataSeriesTableDataSource();
}

</script>
