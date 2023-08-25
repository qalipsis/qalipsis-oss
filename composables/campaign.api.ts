export const useCampaignApi = () => {
    const { get$ } = baseApi();

    /**
     * Fetches the campaigns
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchCampaigns = async (pageQueryParams: PageQueryParams): Promise<Page<Campaign>> => {
        return get$<Page<Campaign>>("/campaigns", pageQueryParams);
    }

    return {
        fetchCampaigns
    }
}