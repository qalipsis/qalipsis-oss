import { Page, PageQueryParams } from "utils/page";
import { DataSeries } from "utils/series";

export const useDataSeriesApi = () => {
    const { get$, delete$, post$ } = baseApi();
    const { getCache$ } = cacheApi();

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
        return getCache$<Page<DataSeries>, PageQueryParams>( `/data-series`, queryParams)
            .then((res: Page<DataSeries>) => {
                const dataSeriesPage: Page<DataSeries> = res;
                dataSeries.push(...res?.elements);
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
        duplicateDataSeries,
        fetchAllDataSeries
    }
}