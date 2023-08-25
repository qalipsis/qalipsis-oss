export const useReportApi = () => {
    const { get$ } = baseApi();

    /**
     * Fetches the campaigns
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchReports = async (pageQueryParams: PageQueryParams): Promise<Page<Report>> => {
        return get$<Page<Report>>("/reports", pageQueryParams);
    }

    return {
        fetchReports
    }
}