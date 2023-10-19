export const useDataSeriesApi = () => {
    const { get$, delete$, post$, patch$ } = baseApi();
    const { getCache$ } = cacheApi();

    /**
     * Fetches the data series
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchDataSeries = async (pageQueryParams: PageQueryParams): Promise<Page<DataSeries>> => {
        return get$<Page<DataSeries>, any>("/data-series", pageQueryParams);
    }

    /**
     * Fetches all data series
     * 
     * @returns All data series
     */
    const fetchAllDataSeries = async (): Promise<DataSeries[]> => {
        return getAllCachedDataSeries({
            page: 0,
            size: 50
        }, []);
    }
    
    /**
     * Fetches the all data series.
     * 
     * @param queryParams The page query params.
     * @param dataSeries The list of fetched data series.
     * @returns The list of all data series.
     */
    const getAllCachedDataSeries = async (queryParams: PageQueryParams, dataSeries: DataSeries[]): Promise<DataSeries[]> => {
        return getCache$<Page<DataSeries>, any>( `/data-series`, queryParams)
            .then((res: Page<DataSeries>) => {
                const dataSeriesPage: Page<DataSeries> = res;
                dataSeries.push(...(res?.elements ?? []));
                if (dataSeriesPage.page < dataSeriesPage.totalPages - 1) {
                    queryParams.page = queryParams.page! + 1;

                    return getAllCachedDataSeries(queryParams, dataSeries)
                } else {
                    return Promise.resolve(dataSeries)
                }
            });
    }

    /**
     * Deletes the data series
     * 
     * @param dataSeriesReferences The data series references to be deleted
     */
    const deleteDataSeries = async (dataSeriesReferences: string[]): Promise<void> => {
        await Promise.all(dataSeriesReferences.map(ref => delete$(`/data-series/${ref}`)));
    }

    /**
     * Creates the data series
     * 
     * @param dataSeriesCreationRequest The request for creating the data series
     */
    const createDataSeries = async (dataSeriesCreationRequest: DataSeriesCreationRequest): Promise<DataSeries> => {
        return post$<DataSeries, DataSeriesCreationRequest>(`/data-series`, dataSeriesCreationRequest);
    }

    /**
     * Duplicates the selected data series
     * 
     * @param dataSeriesTableData The selected data series from the table
     */
    const duplicateDataSeries = async (dataSeriesTableData: DataSeriesTableData): Promise<DataSeries> => {
        const dataSeriesCreationRequest: DataSeriesCreationRequest =  {
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
            filters: dataSeriesTableData.filters ?? []
        };

        return createDataSeries(dataSeriesCreationRequest)
    }

    /**
     * Fetches the available value names for the selected data type
     * 
     * @param dataType The data type.
     * @returns The available value names.
     */
    const fetchValueNames = (dataType: DataType): Promise<string[]> => {
        return get$<string[], unknown>(`/data-series/${dataType}/names`)
    }

    /**
     * Fetches the available data fields for the selected data type
     * 
     * @param dataType The data type.
     * @returns The available fields.
     */
    const fetchFields = (dataType: DataType): Promise<DataField[]> => {
        return get$<DataField[], unknown>(`/data-series/${dataType}/fields`)
    }

    /**
     * Fetches the tags for the tag options
     * 
     * @param dataType The data type
     * @returns A map of the tags
     */
    const fetchTags = (dataType: DataType): Promise<{[key: string]: string[]}> => {
        return get$<{[key: string]: string[]}, unknown>(`/data-series/${dataType}/tags`)
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
            filter: name
        };
        const response = await fetchDataSeries(queryParams);

        return !response.elements?.some((e: DataSeries) => e.displayName.trim() === name.trim());
    }

    /**
     * Updates the data series.
     * 
     * @param reference The identifier of the data series
     * @param patchSeriesRequest A list contains the updated details of the data series
     * @returns The updated details of the data series
     */
    const updateDataSeries = async (reference: string, patchSeriesRequest: DataSeriesPatch[]): Promise<DataSeries> => {
        return patch$<DataSeries, DataSeriesPatch[]>(`/data-series/${reference}`, patchSeriesRequest);
    }

    return {
        fetchDataSeries,
        deleteDataSeries,
        duplicateDataSeries,
        fetchAllDataSeries,
        fetchValueNames,
        fetchFields,
        fetchTags,
        updateDataSeries,
        createDataSeries,
        isValidDisplayName
    }
}