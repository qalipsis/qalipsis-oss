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
        async fetchCampaignsTableDataSource(extraQueryParams?: { [key: string]: string }): Promise<void> {
            const { fetchCampaigns } = useCampaignApi();
            let pageQueryParams: PageQueryParams = {
                page: this.currentPageIndex,
                size: TableHelper.defaultPageSize,
            }

            if (this.filter) {
                pageQueryParams.filter = this.filter;
            }

            if (this.sort) {
                pageQueryParams.sort = this.sort;
            }

            if (extraQueryParams) {
                pageQueryParams = {
                    ...pageQueryParams,
                    ...extraQueryParams
                }
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
