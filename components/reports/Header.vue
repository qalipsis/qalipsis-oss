<template>
    <BaseHeader>
        <div class="flex items-center full-width space-between">
            <h2>Reports</h2>
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
                    :disabled="deleteAllBtnDisabled"
                    :btn-style="'stroke'"
                    :icon="'/icons/icon-delete-small.svg'"
                    @click="handleDeleteSelectedReportsBtnClick" 
                />
                <BaseButton 
                    class="ml-2"
                    text="Create report" 
                    :btn-style="'stroke'"
                    :icon="'/icons/icon-plus-grey.svg'"
                    @click="handleCreateReportBtnClick" 
                />
            </div>
        </div>
    </BaseHeader>
</template>

<script setup lang="ts">

const reportsTableStore = useReportsTableStore();
const reportSearchQuery = ref('');
const deleteAllBtnDisabled = computed(() => reportsTableStore.selectedRows?.length === 0);
const dataSeriesReferences = computed(() => reportsTableStore.selectedRowKeys);
const deleteModalContent = computed(() => `${reportsTableStore.selectedRows.map(r => r.displayName).join(',')}`)
const reportsDeleteConfirmationModal = ref(null);

const handleCreateReportBtnClick = () => {

}

const handleDeleteSelectedReportsBtnClick = () => {
}

const handleSearch = () => {

}

</script>
