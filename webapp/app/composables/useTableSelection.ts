export const useTableSelection = <T extends Record<string, any>>(options: {
  rowKey: Ref<string>
  dataSource: Ref<T[]>
  selectedRowKeys: Ref<string[] | undefined>
  disableRow: Ref<((row: T) => boolean) | undefined>
}) => {
  const { rowKey, dataSource, selectedRowKeys, disableRow } = options

  const rowCache = new Map<string, T>()
  const allSelectedRowKeys = ref<string[]>([])
  const currentPageSelectedRowKeys = ref<string[]>([])
  const rowAllSelectionChecked = ref(false)

  const _cacheRows = () => {
    for (const row of dataSource.value) {
      rowCache.set(row[rowKey.value], row)
    }
  }

  const _sync = () => {
    allSelectedRowKeys.value = selectedRowKeys.value ? [...selectedRowKeys.value] : []
    currentPageSelectedRowKeys.value = dataSource.value
      .map(row => row[rowKey.value])
      .filter(key => allSelectedRowKeys.value.includes(key))
    rowAllSelectionChecked.value =
      dataSource.value.length > 0 &&
      currentPageSelectedRowKeys.value.length === dataSource.value.length
    _cacheRows()
  }

  const isAllSelectionDisabled = computed(() => {
    if (!disableRow.value) return false

    return dataSource.value.every(row => disableRow.value!(row))
  })

  const isRowDisabled = (row: T): boolean => disableRow.value?.(row) ?? false

  const _getAllSelectedRows = (): T[] =>
    allSelectedRowKeys.value
      .map(key => rowCache.get(key))
      .filter((row): row is T => row !== undefined)

  const handleRowSelectionChange = (key: string): TableSelection => {
    if (allSelectedRowKeys.value.includes(key)) {
      allSelectedRowKeys.value = allSelectedRowKeys.value.filter(k => k !== key)
    } else {
      allSelectedRowKeys.value.push(key)
    }

    return { selectedRowKeys: [...allSelectedRowKeys.value], selectedRows: _getAllSelectedRows() }
  }

  const handleRowAllSelectionChange = (checked: string | boolean | string[]): TableSelection => {
    const enabledRowKeys = dataSource.value
      .filter(row => !isRowDisabled(row))
      .map(row => row[rowKey.value])

    if (checked) {
      const preselected = selectedRowKeys.value ?? []
      allSelectedRowKeys.value = [...new Set([...enabledRowKeys, ...preselected])]
      currentPageSelectedRowKeys.value = [...enabledRowKeys]
    } else {
      currentPageSelectedRowKeys.value = []
      allSelectedRowKeys.value = allSelectedRowKeys.value.filter(k => !enabledRowKeys.includes(k))
    }

    return { selectedRowKeys: [...allSelectedRowKeys.value], selectedRows: _getAllSelectedRows() }
  }

  watch([dataSource, selectedRowKeys], _sync)

  return {
    currentPageSelectedRowKeys,
    rowAllSelectionChecked,
    isAllSelectionDisabled,
    isRowDisabled,
    handleRowSelectionChange,
    handleRowAllSelectionChange,
  }
}
