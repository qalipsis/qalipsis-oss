<template>
  <section class="w-full dark:text-gray-100 dark:bg-gray-900 rounded-lg">
    <table class="w-full text-sm">
      <thead class="border-b border-solid border-gray-100 dark:border-gray-700">
        <tr>
          <th
            v-if="rowSelectionEnabled"
            class="w-9"
          >
            <div class="p-2">
              <BaseTableCheckbox
                v-if="rowAllSelectionEnabled"
                value="selectAll"
                v-model="rowAllSelectionChecked"
                @update:model-value="handleRowAllSelectionChange"
                :disabled="isAllSelectionDisabled"
                :indeterminate="!rowAllSelectionChecked && currentPageSelectedRowKeys.length > 0"
              ></BaseTableCheckbox>
            </div>
          </th>
          <template
            v-for="tableColumnConfig in tableColumnConfigs"
            :key="tableColumnConfig.key"
          >
            <th
              class="group"
              :style="tableColumnConfig.width ? { width: tableColumnConfig.width } : {}"
              :class="{
                'hover:bg-primary-50 dark:hover:bg-gray-800 cursor-pointer': tableColumnConfig.sortingEnabled,
              }"
            >
              <div class="flex items-center justify-between">
                <div
                  class="flex flex-grow p-4 items-center justify-between group"
                  @click="tableColumnConfig.sortingEnabled && handleSorterClick(tableColumnConfig.key)"
                >
                  <span class="text-base font-semibold">
                    {{ tableColumnConfig.title }}
                  </span>
                  <BaseTableSorter
                    v-if="tableColumnConfig.sortingEnabled"
                    :sorter-key="tableColumnConfig.key"
                    :active-sorter-key="activeSorterKey"
                    :active-sorter-direction="activeSorterDirection"
                  ></BaseTableSorter>
                </div>
                <div class="h-5 border-r border-solid border-gray-200 group-last:border-r-0"></div>
              </div>
            </th>
          </template>
          <th
            v-if="!refreshHidden || $slots.actionCell"
            class="w-12 px-2"
          >
            <div
              class="flex items-center cursor-pointer"
              @click="emit('refresh')"
            >
              <BaseTooltip text="Refresh">
                <BaseIcon
                  icon="qls-icon-refresh"
                  class="text-2xl text-primary-900 dark:text-gray-100 hover:text-primary-500"
                />
              </BaseTooltip>
            </div>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="record in dataSource"
          class="hover:bg-gray-50 dark:hover:bg-gray-800"
          :key="record[rowKey]"
          :class="[
            currentPageSelectedRowKeys.includes(record[rowKey]) ? 'bg-primary-50 dark:bg-gray-800' : '',
            rowClass ?? '',
          ]"
        >
          <td v-if="rowSelectionEnabled">
            <div class="p-2">
              <BaseTableCheckbox
                v-model="currentPageSelectedRowKeys"
                :value="record[rowKey]"
                :disabled="isRowDisabled(record)"
                @update:model-value="handleRowSelectionChange(record[rowKey])"
              ></BaseTableCheckbox>
            </div>
          </td>
          <td
            v-for="tableColumnConfig in tableColumnConfigs"
            :key="tableColumnConfig.key"
            class="p-4"
          >
            <slot
              name="bodyCell"
              :record="record"
              :column="tableColumnConfig"
            >
              <div>
                {{ record[tableColumnConfig.key] }}
              </div>
            </slot>
          </td>
          <td
            class="px-2"
            v-if="$slots.actionCell"
          >
            <slot
              name="actionCell"
              :record="record"
            ></slot>
          </td>
        </tr>
      </tbody>
    </table>
    <div
      v-if="dataSource.length === 0"
      class="w-full h-32"
    >
      <div class="h-full mt-10 text-gray-300">
        <div class="flex items-center justify-center">
          <BaseIcon
            icon="qls-icon-document"
            class="text-3xl"
          >
          </BaseIcon>
        </div>
        <div class="flex items-center justify-center">
          <span class="font-extralight">No data</span>
        </div>
      </div>
    </div>
    <div
      v-if="dataSource.length > 0"
      class="my-1"
    >
      <BaseTablePaginator
        :page-size="pageSize"
        :total-elements="totalElements"
        :current-page-index="currentPageIndex"
        @page-change="handlePageChange($event)"
      ></BaseTablePaginator>
    </div>
  </section>
</template>

<script setup lang="ts">
const props = defineProps<{
  /**
   * The configurations for the table columns.
   */
  tableColumnConfigs: TableColumnConfig[]

  /**
   * The data source to be used. Should contain only the current page's rows.
   */
  dataSource: any[]

  /**
   * The custom row class.
   */
  rowClass?: string

  /**
   * A flag to indicate if the user can select the row.
   */
  rowSelectionEnabled?: boolean

  /**
   * A flag to indicate if the select all checkbox should be displayed.
   */
  rowAllSelectionEnabled?: boolean

  /**
   * The callback function to check if a row's selection checkbox should be disabled.
   */
  disableRow?: (row: any) => boolean

  /**
   * The key for identifying the selected row.
   */
  rowKey: string

  /**
   * The selected row keys.
   */
  selectedRowKeys?: string[]

  /**
   * The index of the current page.
   */
  currentPageIndex: number

  /**
   * The total number of rows.
   */
  totalElements: number

  /**
   * The number of rows per page.
   */
  pageSize: number

  /**
   * A flag to indicate if the refresh button should be hidden.
   */
  refreshHidden?: boolean
}>()

const emit = defineEmits<{
  (e: 'sorterChange', v: TableSorter | null): void
  (e: 'pageChange', v: number): void
  (e: 'selectionChange', v: TableSelection): void
  (e: 'refresh'): void
}>()

const {
  currentPageSelectedRowKeys,
  rowAllSelectionChecked,
  isAllSelectionDisabled,
  isRowDisabled,
  handleRowSelectionChange: _handleRowSelectionChange,
  handleRowAllSelectionChange: _handleRowAllSelectionChange,
} = useTableSelection({
  rowKey: toRef(props, 'rowKey'),
  dataSource: toRef(props, 'dataSource'),
  selectedRowKeys: toRef(props, 'selectedRowKeys'),
  disableRow: toRef(props, 'disableRow'),
})

const activeSorterKey = ref('')
const activeSorterDirection = ref<'' | SortingDirection>('')

const handleRowSelectionChange = (rowKey: string) => {
  emit('selectionChange', _handleRowSelectionChange(rowKey))
}

const handleRowAllSelectionChange = (checked: string | boolean | string[]) => {
  emit('selectionChange', _handleRowAllSelectionChange(checked))
}

const handleSorterClick = (columnKey: string) => {
  if (columnKey === activeSorterKey.value) {
    // When clicking the same column.
    if (activeSorterDirection.value === '') {
      // No sorting active — set to descending.
      activeSorterDirection.value = 'desc'
      activeSorterKey.value = columnKey
    } else if (activeSorterDirection.value === 'desc') {
      // Descending → ascending.
      activeSorterDirection.value = 'asc'
      activeSorterKey.value = columnKey
    } else {
      // Ascending → reset.
      activeSorterDirection.value = ''
      activeSorterKey.value = ''
    }
  } else {
    // New column clicked — start descending.
    activeSorterDirection.value = 'desc'
    activeSorterKey.value = columnKey
  }

  let sorter: TableSorter | null = null

  if (activeSorterKey.value && activeSorterDirection.value) {
    sorter = {
      key: activeSorterKey.value,
      direction: activeSorterDirection.value,
    }
  }

  emit('sorterChange', sorter)
}

const handlePageChange = (pageIndex: number) => {
  emit('pageChange', pageIndex)
}
</script>
