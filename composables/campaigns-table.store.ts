import { defineStore } from "pinia";

export const useCampaignsTableStore = defineStore("CampaignsTable", {
    state: (): TableStoreState<CampaignTableData> => {
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
        selectedRowKeys: state => state.selectedRows?.length > 0 ? state.selectedRows.map((r: CampaignTableData) => r.key) : []
    },
    actions: {
        async fetchCampaignsTableDataSource(pageSize?: number): Promise<void> {
            const { fetchCampaigns } = useCampaignApi();
            const pageQueryParams: PageQueryParams = {
                page: this.currentPageIndex,
                size: pageSize ?? PageHelper.defaultPageSize,
            }

            if (this.filter) {
                pageQueryParams.filter = this.filter;
            }

            if (this.sort) {
                pageQueryParams.sort = this.sort;
            }

              const response = await fetchCampaigns(pageQueryParams);
              const tableData: CampaignTableData[] = CampaignHelper.toCampaignTableData(response.elements);
              this.dataSource = tableData;
              this.totalElements = response.totalElements;
        }
    }
});
