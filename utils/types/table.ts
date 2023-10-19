export interface TableStoreState<T> {
    currentPageIndex: number;
    filter: string;
    sort: string;
    pageSize: number;
    totalElements: number;
    dataSource: T[];
    selectedRows: T[];
    selectedRowKeys: string[];
}