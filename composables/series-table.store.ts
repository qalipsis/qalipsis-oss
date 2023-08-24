import { defineStore } from "pinia";

export const useSeriesTableStore = defineStore("SeriesTable", {
  state: (): TableStoreState<DataSeriesTableData> => {
    return {
      currentPageIndex: 0,
      filter: '',
      sort: '',
      totalElements: 0,
      dataSource: [],
      selectedRows: []
    }
  },
  getters: {
    currentPageNumber: state => state.currentPageIndex + 1,
    selectedRowKeys: state => state.selectedRows?.length > 0 ? state.selectedRows.map((r: DataSeriesTableData) => r.key) : []
  },
  actions: {
    async fetchDataSeriesTableDataSource(): Promise<void> {
      const { fetchDataSeries } = useDataSeriesApi();
      const userStore = useUserStore();
      const userName = userStore.user.displayName;
      const pageQueryParams: PageQueryParams = {
        page: this.currentPageIndex,
        size: PageHelper.defaultPageSize,
      }

      if (this.filter) {
        pageQueryParams.filter = this.filter;
      }

      if (this.sort) {
        pageQueryParams.sort = this.sort;
      }

      const response = await fetchDataSeries(pageQueryParams);
      const tableData: DataSeriesTableData[] = SeriesHelper.toDataSeriesTableData(response.elements, userName);
      this.dataSource = tableData;
      this.totalElements = response.totalElements;
    }
  }
});
