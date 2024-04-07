<template>
    <BaseHeader>
        <div class="flex items-center w-full justify-between">
            <BaseTitle content="Reports" />
            <div class="flex items-center">
                <BaseSearch 
                    v-model="reportSearchQuery" 
                    placeholder="Search reports..."
                    size="large"
                    :collapsable="true"
                    @search="handleSearch"
                />
                <BaseButton 
                    class="ml-2"
                    text="Delete all"
                    btn-style="outlined"
                    :disabled="deleteAllBtnDisabled"
                    :icon="'/icons/icon-delete-small.svg'"
                    @click="handleDeleteSelectedReportsBtnClick" 
                />
                <BaseButton 
                    class="ml-2"
                    text="Create report" 
                    btn-style="outlined"
                    :icon="'/icons/icon-plus-grey.svg'"
                    @click="handleCreateReportBtnClick" 
                />
            </div>
        </div>
        <ReportsDeleteConfirmationModal
            v-model:open="modalOpen"
            :reportReferences="reportReferences"
            :modalContent="deleteModalContent"
        />
    </BaseHeader>
</template>

<script setup lang="ts">

const reportsTableStore = useReportsTableStore();
const reportSearchQuery = ref('');
const deleteAllBtnDisabled = computed(() => reportsTableStore.selectedRows?.length === 0);
const reportReferences = computed(() => reportsTableStore.selectedRowKeys);
const deleteModalContent = computed(() => `${reportsTableStore.selectedRows.map(r => r.displayName).join(',')}`)
const modalOpen = ref(false);

const handleCreateReportBtnClick = () => {
    navigateTo('reports/new')
}

const handleDeleteSelectedReportsBtnClick = () => {
    modalOpen.value = true;
}

const handleSearch = () => {
    reportsTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(reportSearchQuery.value),
        currentPageIndex: 0
    });
    reportsTableStore.fetchReportsTableDataSource();
}

</script>
