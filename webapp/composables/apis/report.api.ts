import {format} from 'date-fns'

export const useReportApi = () => {
    const {api$, get$, delete$, post$, put$} = baseApi()

    /**
     * Fetches the campaigns
     *
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchReports = async (pageQueryParams: PageQueryParams): Promise<Page<DataReport>> => {
        return get$<Page<DataReport>, any>('/reports', pageQueryParams)
    }

    /**
     * Updates a report.
     *
     * @param reportReference The identifier of the report.
     * @param request The request for updating a report.
     * @returns The updated report.
     */
    const updateReport = (reportReference: string, request: ReportCreationAndUpdateRequest): Promise<DataReport> => {
        return put$<DataReport, ReportCreationAndUpdateRequest>(`/reports/${reportReference}`, request)
    }

    /**
     * Fetches the details of the report
     *
     * @param reference The identifier of the report
     * @returns The details of the report
     */
    const fetchReportDetails = async (reference: string): Promise<DataReport> => {
        const report = await get$<DataReport, unknown>(`/reports/${reference}`)
        report.dataComponents = report.dataComponents ? report.dataComponents.map((d) => ({...d, id: Date.now()})) : []
        return get$<DataReport, unknown>(`/reports/${reference}`)
    }

    const downloadReport = async (reference: string): Promise<void> => {
        // Triggers the report generation
        const reportTask = await post$<ReportTask, unknown>(`/reports/${reference}/render`, null)
        // Download the report
        const blob = await api$<Blob>(`/reports/file/${reportTask.reference}`, {
            method: 'get',
            responseType: 'blob',
            retry: 20, // Retry maximum 20 times
            retryDelay: 500, // Wait 500 ms for the next retry
        })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        const result = format(new Date(), 'yyyy-MM-dd-HH_mm_ss')
        link.download = `${result}-${reference}.pdf`
        document.body.appendChild(link)
        link.click()
        window.URL.revokeObjectURL(url)
    }

    /**
     * Creates a report.
     *
     * @param request The request for creating a report
     * @returns The created report.
     */
    const createReport = async (reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest): Promise<DataReport> => {
        return post$<DataReport, ReportCreationAndUpdateRequest>('/reports', reportCreationAndUpdateRequest)
    }

    /**
     * Deletes the reports
     *
     * @param reportReferences The report references to be deleted
     */
    const deleteReports = async (reportReferences: string[]): Promise<void> => {
        const reportParams = `?report=${reportReferences.join(',')}`
        return delete$(`/reports/${reportParams}`)
    }

    return {
        fetchReports,
        fetchReportDetails,
        createReport,
        downloadReport,
        updateReport,
        deleteReports,
    }
}
