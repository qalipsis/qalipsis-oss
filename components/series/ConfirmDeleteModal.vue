<template>
    <BaseModal 
        v-model:open="modalOpen"
        :title="'Delete Series'"
        confirmBtnText="Delete"
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
    modalContent: string;
    dataSeriesReferences: string[];
}>()

const seriesTableStore = useSeriesTableStore();

const modalOpen = ref(false);

const open = () => {
    modalOpen.value = true;
}

const handleConfirmButtonClick = async () => {
    try {
        const { deleteDataSeries } = useDataSeriesApi();
        await deleteDataSeries(props.dataSeriesReferences);
        modalOpen.value = false;
        NotificationHelper.success(`The data series ${props.modalContent} has been successfully deleted`);
    } catch (error) {
        ErrorHelper.handleHttpRequestError(error)
    }

    // When the selected data reference are greater or equal to the current display data
    if (props.dataSeriesReferences.length > seriesTableStore.dataSource?.length) {
        // Sets the current page index to the previous one. 
        const pageIndex = seriesTableStore.currentPageIndex - 1 >= 0 ? seriesTableStore.currentPageIndex - 1 : 0;
        seriesTableStore.$patch({
            currentPageIndex: pageIndex
        })
    }

    seriesTableStore.fetchDataSeriesTableDataSource();
}

defineExpose({
    open,
})

</script>
