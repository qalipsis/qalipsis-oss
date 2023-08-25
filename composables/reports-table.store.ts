import { defineStore } from "pinia";

export const useReportsTableStore = defineStore("ReportsTable", {
  state: (): TableStoreState<ReportTableData> => {
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
    selectedRowKeys: state => state.selectedRows?.length > 0 ? state.selectedRows.map((r: ReportTableData) => r.reference) : []
  },
  actions: {
    async fetchReportTableDataSource(): Promise<void> {
      const { fetchReports } = useReportApi();
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

      const response = await fetchReports(pageQueryParams);
      const tableData: ReportTableData[] = ReportHelper.toReportTableData(response.elements);
      this.dataSource = tableData;
      this.totalElements = response.totalElements;
    }
  }
});
