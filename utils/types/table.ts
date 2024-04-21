export interface TableColumnConfig {
    title: string;
    key: string;
    sortingEnabled?: boolean;
}

export interface TableSelection {
    selectedRowKeys: string[];
    selectedRows: any[];
}

export type SortingDirection = 'asc' | 'desc';

export interface TableSorter {
    key: string;
    direction: SortingDirection
}

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