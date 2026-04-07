export const useTableLifecycle = (store: any, fetchFn: () => Promise<void>) => {
  const toastStore = useToastStore()

  const fetch = async () => {
    try {
      await fetchFn()
    } catch (error) {
      toastStore.error({ text: ErrorHelper.getErrorMessage(error) })
    }
  }

  onMounted(() => fetch())
  onBeforeUnmount(() => store.$reset())

  const handlePaginationChange = (pageIndex: number) => {
    store.$patch({ currentPageIndex: pageIndex })
    fetch()
  }

  const handleSorterChange = (tableSorter: TableSorter | null) => {
    store.$patch({ sort: tableSorter ? `${tableSorter.key}:${tableSorter.direction}` : '' })
    fetch()
  }

  return { handlePaginationChange, handleSorterChange, refresh: fetch }
}
