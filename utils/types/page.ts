/**
 * Page of records
 */
export interface Page<T> {
    /**
     * The index of the current page.
     */
    page: number;

    /**
     * The total count of pages matching the criteria.
     */
    totalPages: number;

    /**
     * The total count of elements matching the criteria.
     */
    totalElements: number;

    /**
     * The list of elements in the current page
     */
    elements: T[];
}

/**
 * An interface that describes possible parameters that could be set in the request to page data.
 */
export interface PageQueryParams {
    filter?: string;
    sort?: string;
    page?: number;
    size?: number;
}
