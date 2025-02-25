<template>
    <BaseHeader>
        <div class="flex items-center w-full justify-between">
            <BaseTitle content="Reports" />
            <div class="flex items-center">
                <BaseSearch 
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
                    icon="qls-icon-delete"
                    @click="handleDeleteSelectedReportsBtnClick" 
                />
                <BaseButton 
                    class="ml-2"
                    text="Create report" 
                    btn-style="outlined"
                    icon="qls-icon-plus"
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

const handleSearch = (searchTerm: string) => {
    reportsTableStore.$patch({
        filter: TableHelper.getSanitizedQuery(searchTerm),
        currentPageIndex: 0
    });
    reportsTableStore.fetchReportsTableDataSource();
}

</script>
