export const useReportApi = () => {
    const { get$, delete$, post$ } = baseApi();

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
     * Fetches the details of the report
     * 
     * @param reference The identifier of the report
     * @returns The details of the report
     */
    const fetchReportDetails = async (reference: string): Promise<Report> => {
        return get$<Report>(`/reports/${reference}`);
    }

    /**
     * Creates a report.
     * 
     * @param request The request for creating a report
     * @returns The created report.
     */
    const createReport = async (reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest): Promise<Report> => {
        return post$<Report, ReportCreationAndUpdateRequest>("/reports", reportCreationAndUpdateRequest);
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
        fetchReportDetails,
        createReport,
        deleteReports
    }
}