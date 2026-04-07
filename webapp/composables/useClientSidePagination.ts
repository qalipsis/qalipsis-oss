export const useClientSidePagination = <T>(
  data: Ref<T[]>,
  pageSize: Ref<number> | number,
) => {
  const currentPageIndex = ref(0)
  const sorter = ref<TableSorter | null>(null)
  const _pageSize = isRef(pageSize) ? pageSize : ref(pageSize)

  const totalElements = computed(() => data.value.length)

  const _sortedData = computed(() => {
    if (!sorter.value) return data.value
    const { key, direction } = sorter.value
    const sorted = [...data.value].sort((a: any, b: any) =>
      String(a[key]).localeCompare(String(b[key]))
    )

    return direction === 'desc' ? sorted.reverse() : sorted
  })

  const pagedData = computed<T[]>(() => {
    const start = currentPageIndex.value * _pageSize.value

    return _sortedData.value.slice(start, start + _pageSize.value)
  })

  // Reset to first page when the dataset changes (e.g. after a filter/refresh).
  watch(data, () => {
    currentPageIndex.value = 0
  })

  const handlePageChange = (pageIndex: number) => {
    currentPageIndex.value = pageIndex
  }

  const handleSorterChange = (newSorter: TableSorter | null) => {
    sorter.value = newSorter
    currentPageIndex.value = 0
  }

  return {
    pagedData,
    totalElements,
    currentPageIndex,
    handlePageChange,
    handleSorterChange,
  }
}
