export const useDataSeriesApi = () => {
    const { get$, delete$, post$ } = baseApi();
    /**
     * Fetches the data series
     * 
     * @param pageQueryParams The query parameters
     * @returns The page list of data series
     */
    const fetchDataSeries = async (pageQueryParams: PageQueryParams): Promise<Page<DataSeries>> => {
        return get$<Page<DataSeries>>("/data-series", pageQueryParams);
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
            filters: dataSeriesTableData.filters,
            fieldName: dataSeriesTableData.fieldName,
            aggregationOperation: dataSeriesTableData.aggregationOperation,
            timeframeUnit: dataSeriesTableData.timeframeUnit,
            displayFormat: dataSeriesTableData.displayFormat
        };

        return createDataSeries(dataSeriesCreationRequest)
    }

    return {
        fetchDataSeries,
        deleteDataSeries,
        duplicateDataSeries
    }
}