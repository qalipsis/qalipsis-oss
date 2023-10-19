import { defineStore } from "pinia";

export const useSeriesTableStore = defineStore("SeriesTable", {
  state: (): TableStoreState<DataSeriesTableData> => {
    return {
      currentPageIndex: 0,
      filter: '',
      sort: '',
      pageSize: TableHelper.defaultPageSize,
      totalElements: 0,
      dataSource: [],
      selectedRows: [],
      selectedRowKeys: [],
    }
  },
  getters: {
    currentPageNumber: state => state.currentPageIndex + 1,
  },
  actions: {
    async fetchDataSeriesTableDataSource(): Promise<void> {
      const { fetchDataSeries } = useDataSeriesApi();
      const userStore = useUserStore();
      const userName = userStore.user!.displayName;
      const pageQueryParams: PageQueryParams = {
        page: this.currentPageIndex,
        size: this.pageSize
      }

      if (this.filter) {
        pageQueryParams.filter = this.filter;
      }

      if (this.sort) {
        pageQueryParams.sort = this.sort;
      }

      const response = await fetchDataSeries(pageQueryParams);
      const tableData: DataSeriesTableData[] = SeriesHelper.toDataSeriesTableData(response.elements ?? [], userName);
      this.dataSource = tableData;
      this.totalElements = response.totalElements;
    }
  }
});
