export const useReportApi = () => {
    const { get$, delete$ } = baseApi();

    /**
     * Fetches the campaigns
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchReports = async (pageQueryParams: PageQueryParams): Promise<Page<Report>> => {
        return get$<Page<Report>>("/reports", pageQueryParams);
    }

    /**
     * Deletes the reports
     * 
     * @param reportReferences The report references to be deleted
     */
    const deleteReports = async (reportReferences: string[]): Promise<void> => {
        /**
         * FIXME: 
         * It should be modified when the BE supports to deletion of several reports by the references in one call.
         */
        await Promise.all(reportReferences.map(ref => delete$(`/reports/${ref}`)));
    }


    return {
        fetchReports,
        deleteReports
    }
}