
export const useReportsTableStore = defineStore("ReportsTable", {
  state: (): TableStoreState<ReportTableData> => {
    return {
      currentPageIndex: 0,
      filter: '',
      sort: '',
      pageSize: TableHelper.defaultPageSize,
      totalElements: 0,
      dataSource: [],
      selectedRows: [],
      selectedRowKeys: []
    }
  },
  actions: {
    async fetchReportsTableDataSource(): Promise<void> {
      const { fetchReports } = useReportApi();
      const pageQueryParams: PageQueryParams = {
        page: this.currentPageIndex,
        size: this.pageSize,
      }

      if (this.filter) {
        pageQueryParams.filter = this.filter;
      }

      if (this.sort) {
        pageQueryParams.sort = this.sort;
      }

      const response = await fetchReports(pageQueryParams);
      const tableData: ReportTableData[] = ReportHelper.toReportTableData(response.elements ?? []);
      this.dataSource = tableData;
      this.totalElements = response.totalElements;
    }
  }
});
