export interface TableStoreState<T> {
    currentPageIndex: number;
    filter: string;
    sort: string;
    totalElements: number;
    dataSource: T[];
    selectedRows?: T[];
}