import { defineStore } from "pinia";

export const useCampaignsTableStore = defineStore("CampaignsTable", {
    state: (): TableStoreState<CampaignTableData> => {
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
    getters: {
        currentPageNumber: state => state.currentPageIndex + 1,
    },
    actions: {
        async fetchCampaignsTableDataSource(pageSize?: number): Promise<void> {
            const { fetchCampaigns } = useCampaignApi();
            const pageQueryParams: PageQueryParams = {
                page: this.currentPageIndex,
                size: pageSize ?? TableHelper.defaultPageSize,
            }

            if (this.filter) {
                pageQueryParams.filter = this.filter;
            }

            if (this.sort) {
                pageQueryParams.sort = this.sort;
            }

            try {
                const response: Page<Campaign> = await fetchCampaigns(pageQueryParams);
                this.dataSource = CampaignHelper.toTableData(response.elements ?? []);
                this.totalElements = response.totalElements;
            } catch (error) {
                throw error
            }
        }
    }
});
