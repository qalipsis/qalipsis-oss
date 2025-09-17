<template>
  <BaseModal
      :open="open"
      :title="'Delete reports'"
      confirmBtnText="Delete"
      @close="emits('update:open', false)"
      @confirmBtnClick="handleConfirmButtonClick"
  >
    <section>
      <div>Do you want to delete the following reports:</div>
      <span class="text-bold">{{ modalContent }}</span>
    </section>
  </BaseModal>
</template>

<script setup lang="ts">
const props = defineProps<{
  open: boolean;
  modalContent: string;
  reportReferences: string[];
}>()
const emits = defineEmits<{
  (e: 'update:open', v: boolean): void
}>()
const reportsTableStore = useReportsTableStore();
const toastStore = useToastStore();

const handleConfirmButtonClick = async () => {
  try {
    const {deleteReports} = useReportApi();
    await deleteReports(props.reportReferences);
    emits('update:open', false)
    toastStore.success({text: `Successfully delete ${props.modalContent}`});
  } catch (error) {
    toastStore.error({text: ErrorHelper.getErrorMessage(error)});
  }

  // When the selected data reference are greater or equal to the current display data
  let pageIndex = reportsTableStore.currentPageIndex;
  if (props.reportReferences.length > reportsTableStore.dataSource?.length) {
    // Sets the current page index to the previous one.
    pageIndex = reportsTableStore.currentPageIndex - 1 >= 0 ? reportsTableStore.currentPageIndex - 1 : 0;
  }

  reportsTableStore.$patch({
    currentPageIndex: pageIndex,
    selectedRows: [],
    selectedRowKeys: []
  })

  reportsTableStore.fetchReportsTableDataSource();
}

</script>
