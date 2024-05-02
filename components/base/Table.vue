<template>
  <section class="w-full">
    <table class="w-full text-sm">
      <thead class="border-b border-solid border-gray-100">
        <tr>
          <th v-if="rowSelectionEnabled" class="w-9">
            <div class="p-2">
              <BaseCheckbox
                v-if="rowAllSelectionEnabled"
                value="selectAll"
                v-model="rowAllSelectionChecked"
                @update:model-value="handleRowAllSelectionChange"
                :disabled="disabledRowAllSelection()"
                :indeterminate="!rowAllSelectionChecked && currentPageSelectedRowKeys.length > 0"
              ></BaseCheckbox>
            </div>
          </th>
          <template
            v-for="tableColumnConfig in tableColumnConfigs"
            :key="tableColumnConfig.key"
          >
            <th
              class="group"
              :class="{
                'hover:bg-primary-50 cursor-pointer':
                  tableColumnConfig.sortingEnabled,
              }"
            >
              <div class="flex items-center justify-between">
                <div
                  class="flex flex-grow p-4 items-center justify-between group"
                  @click="
                    tableColumnConfig.sortingEnabled &&
                      handleSorterClick(tableColumnConfig.key)
                  "
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
          <th v-if="!refreshHidden || $slots.actionCell" class="min-w-16 px-2">
            <div
              class="flex items-center cursor-pointer"
              @click="emit('refresh')"
            >
              <BaseTooltip text="Refresh">
                <BaseIcon
                  icon="/icons/icon-refresh.svg"
                  :class="TailwindClassHelper.primaryColorFilterHoverClass"
                />
              </BaseTooltip>
            </div>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr 
          v-for="record in displayRows"
          class="hover:bg-gray-50"
          :key="record[rowKey]"
          :class="[
            currentPageSelectedRowKeys.includes(record[rowKey]) ? 'bg-primary-50' : '',
            rowClass ?? ''
          ]"
        >
          <td v-if="rowSelectionEnabled">
            <div class="p-2">
              <BaseCheckbox
                ref="tableRowCheckboxes"
                v-model="currentPageSelectedRowKeys"
                :value="record[rowKey]"
                :disabled="disableRowSelection(record)"
                @update:model-value="handleRowSelectionChange(record[rowKey])"
              ></BaseCheckbox>
            </div>
          </td>
          <td
            v-for="tableColumnConfig in tableColumnConfigs"
            :key="tableColumnConfig.key"
            class="p-4"
          >
            <slot name="bodyCell" :record="record" :column="tableColumnConfig">
              <div>
                {{ record[tableColumnConfig.key] }}
              </div>
            </slot>
          </td>
          <td class="px-2" v-if="$slots.actionCell" >
            <slot name="actionCell" :record="record"></slot>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="displayRows.length === 0" class="w-full h-32">
      <div class="h-full mt-10">
        <div class="flex items-center justify-center">
          <BaseIcon
            class="w-10" icon="/icons/icon-document.svg"
            :class="TailwindClassHelper.disableColorFilterClass"
          >
          </BaseIcon>
        </div>
        <div class="flex items-center justify-center">
          <span class="text-gray-300 font-extralight">No data</span>
        </div>
      </div>
    </div>
    <div v-if="displayRows.length > 0" class="my-1">
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
  tableColumnConfigs: TableColumnConfig[];

  /**
   * The data source to be used.
   */
  dataSource: any[];

  /**
   * A flag to indicate if the data source contains all data.
   */
  allDataSourceIncluded?: boolean;

  /**
   * The custom row class.
   */
  rowClass?: string;

  /**
   * A flag to indicate if the user can select the row.
   */
  rowSelectionEnabled?: boolean;

  /**
   * A flag to indicate if the select all checkbox should be displayed
   */
  rowAllSelectionEnabled?: boolean;

  /**
   * The callback function to check if the selection checkbox can be enabled
   */
  disableRow?: (row: any) => boolean;

  /**
   * A flag to indicate if the user can select all rows.
   */
  selectAllEnabled?: boolean;

  /**
   * The key for identifying the selected row.
   */
  rowKey: string;

  /**
   * The selected row keys.
   */
  selectedRowKeys?: string[];

  /**
   * The index of the current page.
   */
  currentPageIndex: number;

  /**
   * The total number of rows.
   */
  totalElements: number;

  /**
   * The number of rows per page.
   */
  pageSize: number;
  
  /**
   * A flag to indicate if the refresh button should be hidden
   */
  refreshHidden?: boolean;
}>();

const emit = defineEmits<{
  (e: "sorterChange", v: TableSorter | null): void;
  (e: "pageChange", v: number): void;
  (e: "selectionChange", v: TableSelection): void;
  (e: "refresh"): void;
}>();

// The reference to get all checkboxes from all rows.
const tableRowCheckboxes = ref<any>([]);

/**
 * The selected row keys from all pages.
 */
const allSelectedRowKeys = ref<string[]>([]);

/**
 * The rows to be displayed.
 */
const displayRows = ref<any[]>([]);

/**
 * All rows from all data sources. 
 */
const cachedRows = ref<any[]>([]);

/**
 * The selected row keys on the current page.
 */
const currentPageSelectedRowKeys = ref<string[]>([]);

const rowAllSelectionChecked = ref(false);

const activeSorterKey = ref("");
const activeSorterDirection = ref<"" | SortingDirection>("");

watch(() => props.dataSource, () => {
  currentPageSelectedRowKeys.value = props.dataSource
    .map(rowKey => rowKey[props.rowKey])
    .filter(rowKey => allSelectedRowKeys.value.includes(rowKey) || props.selectedRowKeys?.includes(rowKey));
  rowAllSelectionChecked.value = currentPageSelectedRowKeys.value.length === props.dataSource.length;
  allSelectedRowKeys.value = props.selectedRowKeys ? [...props.selectedRowKeys] : [];
  _updateDisplayRows();
  _updateCachedRows();
})

const disabledRowAllSelection = () => {
  const disabledRows = tableRowCheckboxes.value
    .map((v: CheckBoxExposeType) => v.disabled)
    .filter((v: boolean) => v);
  
  return disabledRows.length === props.dataSource.length;
}

const disableRowSelection = (row: any) => {
  if (props.disableRow) {
    return props.disableRow(row);
  }

  return false;
}

const handleRowAllSelectionChange = (checked: string | boolean | string[]) => {
  const disabledRows = tableRowCheckboxes.value
    .map((v: CheckBoxExposeType) => v.disabled);

  // Finds the enabled row keys from the table. 
  const enabledRowKeys = props.dataSource
    .map(d => d[props.rowKey])
    .filter((_, i) => disabledRows[i] !== true);

  if (checked) {
    const preselectedRowKeys = props.selectedRowKeys ?? []
    allSelectedRowKeys.value = [...new Set([...enabledRowKeys, ...preselectedRowKeys])]
    currentPageSelectedRowKeys.value = [...enabledRowKeys];
  } else {
    currentPageSelectedRowKeys.value = [];
    allSelectedRowKeys.value = allSelectedRowKeys.value.filter(rowKey => !enabledRowKeys.includes(rowKey)); 
  }

  emit('selectionChange', {
    selectedRowKeys: allSelectedRowKeys.value,
    selectedRows: getAllSelectedRows()
  });
}

const handleRowSelectionChange = (rowKey: string) => {
  if (allSelectedRowKeys.value.includes(rowKey)) {
    allSelectedRowKeys.value = allSelectedRowKeys.value.filter(selectedRowKey => selectedRowKey !== rowKey);
  } else {
    allSelectedRowKeys.value.push(rowKey)
  }

  emit('selectionChange', {
    selectedRowKeys: allSelectedRowKeys.value,
    selectedRows: getAllSelectedRows()
  });
}

const handleSorterClick = (columnKey: string) => {
  if (columnKey === activeSorterKey.value) {
    // When clicking the same column.
    if (activeSorterDirection.value === "") {
      // When there is no sorting enabled, the sorting direction is empty string.
      activeSorterDirection.value = "asc";
      activeSorterKey.value = columnKey;
    } else if (activeSorterDirection.value === "asc") {
      // When the sorting direction is asc, change the sorting direction to be desc.
      activeSorterDirection.value = "desc";
      activeSorterKey.value = columnKey;
    } else {
      // Resets the sorter
      activeSorterDirection.value = "";
      activeSorterKey.value = "";
    }
  } else {
    // When clicking another column.
    activeSorterDirection.value = "asc";
    activeSorterKey.value = columnKey;
  }

  let sorter: TableSorter | null = null;

  if (activeSorterDirection.value && activeSorterDirection.value) {
    sorter = {
      key: activeSorterKey.value,
      direction: activeSorterDirection.value,
    };
  }

  emit("sorterChange", sorter);

  if (props.allDataSourceIncluded) {
    _sortDisplayRowsFromDataSource(sorter);
  }
};

const handlePageChange = (pageIndex: number) => {
  emit("pageChange", pageIndex);

  if (props.allDataSourceIncluded) {
    _setDisplayRowsFromDataSource(pageIndex);
  }
};

const getAllSelectedRows = () => {
  return cachedRows.value.filter(row => allSelectedRowKeys.value.includes(row[props.rowKey]));
}


const _updateDisplayRows = () => {
  if (props.allDataSourceIncluded) {
    _setDisplayRowsFromDataSource(props.currentPageIndex);
  } else {
    displayRows.value = [...props.dataSource];
  }
}

const _updateCachedRows = () => {
  if (props.allDataSourceIncluded) {
    _setAllCachedRows();
  } else {
    _setCachedRows();
  }
}

const _sortDisplayRowsFromDataSource = (sorter: TableSorter | null) => {
  if (sorter) {
    const { key, direction} = sorter;
    const sortedRows =  displayRows.value.sort((a, b) => a[key].localeCompare(b[key]));
    displayRows.value = direction === 'desc' ? sortedRows.reverse() : sortedRows;
  } else {
    _setDisplayRowsFromDataSource(props.currentPageIndex);
  }
}

const _setDisplayRowsFromDataSource = (pageIndex: number) => {
  const startIndex = pageIndex * props.pageSize;
  const endIndex = (pageIndex * props.pageSize) + props.pageSize;
  displayRows.value = [...props.dataSource].slice(startIndex, endIndex);
}

const _setCachedRows = () => {
  for (let i = 0; i < props.dataSource.length; i++) {
    const cachedRowIndex = props.currentPageIndex * props.pageSize + i;
    cachedRows.value[cachedRowIndex] = props.dataSource[i];
  }
}

const _setAllCachedRows = () => {
  for (let i = 0; i < props.dataSource.length; i++) {
    cachedRows.value[i] = props.dataSource[i];
  }
}

</script>
