import { Report, ReportCreationAndUpdateRequest } from "../utils/report";
import { Page } from "../utils/page";
import { PageQueryParams } from "../utils/page";

export const useReportApi = () => {
    const { get$, delete$, post$, put$ } = baseApi();

    /**
     * Fetches the campaigns
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchReports = async (pageQueryParams: PageQueryParams): Promise<Page<Report>> => {
        return get$<Page<Report>, any>("/reports", pageQueryParams);
    }

    /**
     * Updates a report.
     * 
     * @param reportReference The identifier of the report.
     * @param request The request for updating a report.
     * @returns The updated report.
     */
    const updateReport = (reportReference: string, request: ReportCreationAndUpdateRequest): Promise<Report> => {
        return put$<Report, ReportCreationAndUpdateRequest>(`/reports/${reportReference}`, request);
    }

    /**
     * Fetches the details of the report
     * 
     * @param reference The identifier of the report
     * @returns The details of the report
     */
    const fetchReportDetails = async (reference: string): Promise<Report> => {
        const report = await get$<Report, unknown>(`/reports/${reference}`);
        report.dataComponents = report.dataComponents.map(d => ({...d, id: Date.now()}))
        return get$<Report, unknown>(`/reports/${reference}`);
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
        updateReport,
        deleteReports
    }
}