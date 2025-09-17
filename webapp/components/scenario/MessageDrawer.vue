<template>
  <BaseDrawer
      :open="open"
      :title="title"
      :maskClosable="true"
      :footer-hidden="true"
      :width="920"
      @close="emit('update:open', false)"
  >
    <div class="flex justify-end w-full mb-3">
      <BaseSearch
          :collapsable="false"
          placeholder="Search message..."
          @search="handleSearch"
      />
    </div>
    <BaseTable
        :data-source="tableData"
        :table-column-configs="ScenarioDetailsConfig.NEW_MESSAGE_TABLE_COLUMNS"
        :page-size="TableHelper.defaultPageSize"
        :current-page-index="currentPageIndex"
        :total-elements="tableData.length"
        :all-data-source-included="true"
        :refresh-hidden="true"
        row-key="messageId"
        @page-change="handlePaginationPage"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'severity'">
          <BaseTag
              class="cursor-pointer"
              :text="record.severityTag.text"
              :text-css-class="record.severityTag.textCssClass"
              :background-css-class="record.severityTag.backgroundCssClass"/>
        </template>
      </template>
    </BaseTable>
  </BaseDrawer>
</template>

<script setup lang="ts">

const props = defineProps<{
  open: boolean,
  title: string,
  messages: ReportMessage[];
}>()

const emit = defineEmits<{
  (e: "update:open", v: boolean): void
}>()

/**
 * The table data to be displayed
 */
const tableData = ref<ReportMessage[]>([]);

const currentPageIndex = ref<number>(0);

const handlePaginationPage = (pageIndex: number) => {
  currentPageIndex.value = pageIndex;
}

onMounted(() => {
  tableData.value = [...props.messages];
})

const handleSearch = (searchTerm: string) => {
  if (searchTerm) {
    currentPageIndex.value = 0;
    tableData.value = SearchHelper.performFuzzySearch(
        searchTerm, props.messages, ["stepName", "severity", "message"])
  } else {
    tableData.value = [...props.messages];
  }
}

</script>
