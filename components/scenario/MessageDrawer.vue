<template>
    <BaseDrawer :open="open" :title="title" :maskClosable="true" :footer-hidden="true" :width="920"
        @close="emit('update:open', false)">
        <div class="flex content-end full-width mb-3">
            <BaseSearch v-model="query" :collapsable="false" placeholder="Search message..."/>
        </div>
        <a-table 
            :data-source="tableData"
            :columns="messageTableColumnConfigs"
            :pagination="paginationOptions">
            <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'severity'">
                    <BaseTag
                        class="cursor-pointer"
                        :text="record.severityTag.text"
                        :text-css-class="record.severityTag.textCssClass"
                        :background-css-class="record.severityTag.backgroundCssClass" />
                </template>
            </template>
        </a-table>
    </BaseDrawer>
</template>

<script setup lang="ts">

const props = defineProps<{
    open: boolean,
    title: string,
    messages: ReportMessage[];
}>()

/**
 * The current page index for the the pagination.
 * 
 * @remarks
 * The start page index for the pagination is 1.
 */
const currentPage = ref(1);

const emit = defineEmits<{
    (e: "update:open", v: boolean): void
}>()

const query = ref("");

/**
 * The table data to be displayed
 */
const tableData = computed(() => {
    if (query.value) {
        currentPage.value = 1
        return SearchHelper.performFuzzySearch(
            query.value, props.messages, ["stepName", "severity", "message"])
    }

    return props.messages;
});

const messageTableColumnConfigs = ScenarioHelper.getMessageTableColumnConfigs();

const paginationOptions = reactive({
    ...TableHelper.sharedPaginationProperties,
    current: currentPage,
    pageSize: TableHelper.defaultPageSize,
    total: tableData.value.length,
    onChange: function (page: number, _: number): void {
        currentPage.value = page;
    }
});

</script>
