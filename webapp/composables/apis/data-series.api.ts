let _allDataSeries: DataSeries[] = []
const _campaignDataSeriesCache = new Map<string, DataSeries[]>()

export const useDataSeriesApi = () => {
  const { get$, delete$, post$, patch$ } = baseApi()

  /**
   * Fetches the data series
   *
   * @param pageQueryParams The query parameters
   * @returns The page list of data series
   */
  const fetchDataSeries = async (pageQueryParams: DataSeriesPageQueryParams): Promise<Page<DataSeries>> => {
    return get$<Page<DataSeries>, DataSeriesPageQueryParams>('/data-series', pageQueryParams)
  }

  const getAllCachedDataSeries = async (): Promise<DataSeries[]> => {
    return Promise.resolve(_allDataSeries)
  }

  const storeAllDataSeriesToCache = (dataSeries: DataSeries[]) => {
    _allDataSeries = [...dataSeries]
  }

  /**
   * Fetches all data series
   *
   * @returns All data series
   */
  const fetchAllDataSeries = () => {
    return _fetchAllDataSeriesRecursively({ page: 0, size: 100 }, [])
  }

  /**
   * Fetches all data series for the given params, with results cached per param set.
   *
   * @param params The query params (supports optional campaign key).
   * @returns All matching data series.
   */
  const getCachedDataSeries = async (params: DataSeriesPageQueryParams): Promise<DataSeries[]> => {
    const cacheKey = JSON.stringify(params)
    if (_campaignDataSeriesCache.has(cacheKey)) {
      return _campaignDataSeriesCache.get(cacheKey)!
    }
    const result = await _fetchAllDataSeriesRecursively({ page: 0, size: 100, ...params }, [])
    _campaignDataSeriesCache.set(cacheKey, result)

    return result
  }

  /**
   * Fetches all data series recursively page by page.
   *
   * @param queryParams The page query params.
   * @param dataSeries The accumulated list of fetched data series.
   * @returns The complete list of all data series.
   */
  const _fetchAllDataSeriesRecursively = async (
    queryParams: DataSeriesPageQueryParams,
    dataSeries: DataSeries[],
  ): Promise<DataSeries[]> => {
    const res = await get$<Page<DataSeries>, DataSeriesPageQueryParams>('/data-series', queryParams)
    dataSeries.push(...(res?.elements ?? []))
    if (res.page < res.totalPages - 1) {
      queryParams.page = (queryParams.page ?? 0) + 1

      return _fetchAllDataSeriesRecursively(queryParams, dataSeries)
    }

    return dataSeries
  }

  /**
   * Deletes the data series
   *
   * @param dataSeriesReferences The data series references to be deleted
   */
  const deleteDataSeries = async (dataSeriesReferences: string[]): Promise<void> => {
    return delete$(`/data-series?series=${dataSeriesReferences.join(',')}`)
  }

  /**
   * Creates the data series
   *
   * @param dataSeriesCreationRequest The request for creating the data series
   */
  const createDataSeries = async (dataSeriesCreationRequest: DataSeriesCreationRequest): Promise<DataSeries> => {
    return post$<DataSeries>('/data-series', dataSeriesCreationRequest)
  }

  /**
   * Duplicates the selected data series
   *
   * @param dataSeriesTableData The selected data series from the table
   */
  const duplicateDataSeries = async (dataSeriesTableData: DataSeriesTableData): Promise<DataSeries> => {
    const dataSeriesCreationRequest: DataSeriesCreationRequest = {
      displayName: `Copy of ${dataSeriesTableData.displayName}`,
      valueName: dataSeriesTableData.valueName,
      dataType: dataSeriesTableData.dataType,
      sharingMode: dataSeriesTableData.sharingMode,
      color: dataSeriesTableData.color,
      colorOpacity: dataSeriesTableData.colorOpacity,
      fieldName: dataSeriesTableData.fieldName,
      aggregationOperation: dataSeriesTableData.aggregationOperation,
      timeframeUnit: dataSeriesTableData.timeframeUnit,
      displayFormat: dataSeriesTableData.displayFormat,
      filters: dataSeriesTableData.filters ?? [],
    }

    return createDataSeries(dataSeriesCreationRequest)
  }

  /**
   * Fetches the available value names for the selected data type
   *
   * @param dataType The data type.
   * @param query Optional filter string.
   * @returns The available value names.
   */
  const fetchValueNames = (dataType: DataType, query?: string): Promise<string[]> => {
    return get$<string[]>(`/data-series/${dataType}/names`, query ? { filter: `*${query}*`, size: 20 } : undefined)
  }

  /**
   * Fetches the available data fields for the selected data type
   *
   * @param dataType The data type.
   * @returns The available fields.
   */
  const fetchFields = (dataType: DataType): Promise<DataField[]> => {
    return get$<DataField[]>(`/data-series/${dataType}/fields`)
  }

  /**
   * Fetches the tags for the tag options
   *
   * @param dataType The data type
   * @returns A map of the tags
   */
  const fetchTags = (dataType: DataType): Promise<{ [key: string]: string[] }> => {
    return get$<{ [key: string]: string[] }>(`/data-series/${dataType}/tags`)
  }

  /**
   * Check if the name of the series exists.
   *
   * @param name The name of the series.
   * @returns If the name of the series already exists.
   */
  const isValidDisplayName = async (name: string): Promise<boolean> => {
    const queryParams: PageQueryParams = {
      size: 1,
      filter: name,
    }
    const response = await fetchDataSeries(queryParams)

    return !response.elements?.some((e: DataSeries) => e.displayName.trim() === name.trim())
  }

  /**
   * Updates the data series.
   *
   * @param reference The identifier of the data series
   * @param patchSeriesRequest A list contains the updated details of the data series
   * @returns The updated details of the data series
   */
  const updateDataSeries = async (reference: string, patchSeriesRequest: DataSeriesPatch[]): Promise<DataSeries> => {
    return patch$<DataSeries, DataSeriesPatch[]>(`/data-series/${reference}`, patchSeriesRequest)
  }

  return {
    fetchDataSeries,
    deleteDataSeries,
    duplicateDataSeries,
    fetchAllDataSeries,
    getCachedDataSeries,
    fetchValueNames,
    fetchFields,
    fetchTags,
    updateDataSeries,
    createDataSeries,
    isValidDisplayName,
    storeAllDataSeriesToCache,
    getAllCachedDataSeries,
  }
}
