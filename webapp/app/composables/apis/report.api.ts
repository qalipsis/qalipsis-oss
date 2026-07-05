export const useReportApi = () => {
  const {apiRaw$, get$, delete$, post$, put$} = baseApi()

  /**
   * Fetches the reports
   *
   * @param pageQueryParams The query parameters
   * @returns The paginated list of reports
   */
  const fetchReports = async (pageQueryParams: PageQueryParams): Promise<Page<DataReport>> => {
    return get$<Page<DataReport>, PageQueryParams>('/reports', pageQueryParams)
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
    const report = await get$<DataReport>(`/reports/${reference}`)
    report.dataComponents = report.dataComponents ? report.dataComponents.map((d: DataComponent) => ({ ...d, id: Date.now() })) : []

    return report
  }

  /**
   * Downloads a report as a PDF file.
   * Triggers report generation, then polls the file endpoint (up to 20 retries, 500ms apart)
   * until the file is ready, then initiates a browser download.
   *
   * @param reference The identifier of the report
   */
  const downloadReport = async (reference: string): Promise<void> => {
    // Triggers the report generation
    const reportTask = await post$<ReportTask>(`/reports/${reference}/render`)
    // Download the report
    const response = await apiRaw$<Blob>(`/reports/file/${reportTask.reference}`, {
      method: 'get',
      responseType: 'blob',
      retry: 20, // Retry maximum 20 times
      retryDelay: 1000, // Wait 1000 ms for the next retry
      retryStatusCodes: [408, 409, 422, 425, 429, 502, 503, 504],
    })
    const blob = response._data as Blob
    const filename = filenameFromContentDisposition(response.headers.get('content-disposition'))
        ?? `${reference}.pdf`
    const url = globalThis.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    globalThis.URL.revokeObjectURL(url)
  }

  /**
   * Extracts the filename from a Content-Disposition header.
   * Supports both `filename*=UTF-8''<encoded>` (RFC 5987) and `filename="..."` forms.
   * Returns undefined if no filename is present.
   */
  const filenameFromContentDisposition = (header: string | null | undefined): string | undefined => {
    if (!header) return undefined
    const encodedMatch = /filename\*\s*=\s*[^']*''([^;]+)/i.exec(header)
    if (encodedMatch?.[1]) {
      try {
        return decodeURIComponent(encodedMatch[1].trim())
      } catch {
        // Fall through to the plain filename form.
      }
    }
    const plainMatch = /filename\s*=\s*"?([^";]+)"?/i.exec(header)
    return plainMatch?.[1]?.trim()
  }

  /**
   * Creates a report.
   *
   * @param request The request for creating a report
   * @returns The created report.
   */
  const createReport = async (reportCreationAndUpdateRequest: ReportCreationAndUpdateRequest): Promise<DataReport> => {
    return post$<DataReport>('/reports', reportCreationAndUpdateRequest)
  }

  /**
   * Deletes the reports
   *
   * @param reportReferences The report references to be deleted
   */
  const deleteReports = async (reportReferences: string[]): Promise<void> => {
    return delete$(`/reports?report=${reportReferences.join(',')}`)
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
