import type { SorterResult } from "ant-design-vue/es/table/interface";
import type { TablePaginationConfig } from 'ant-design-vue/es/table/Table';

export class TableHelper {
    static defaultPageSize = 10;

    static getSanitizedQuery = (query: string): string => {
        /**
         * Steps for sanitizing the query
         * 1. trims the spaces
         * 2. removes the double quotation
         * 3. replaces the white spaces to comma
         * 4. splits the query by comma
         * 5. trims the space of each value in the query
         * 6. filters the empty value
         * 7. join the text by comma
         */ 
        return query
            .trim()
            .replace(/"/g, '')
            .replace(/ /g, ',')
            .split(',')
            .map(text => text.trim())
            .filter(text => text)
            .join(',')
    }

    static sharedPaginationProperties: TablePaginationConfig = {
        showQuickJumper: true,
        position: ['bottomLeft'],
        showTotal(total: number, range: [number, number]): string {
            return `Showing ${range[0]} - ${range[1]} of ${total}`;
        },
        locale: { jump_to: "Go to page" }
    }

    static getRowsFromDataSource = <T>(pageSize: number, pageIndex: number, dataSources: T[]) => {
        return []
    }

    static getCurrentPageIndex = (pagination: TablePaginationConfig): number => {
        return pagination.current! - 1;
    }

    static getSort = (sorter: SorterResult): string => {
        const sortKey = sorter.column ? sorter.column.key : "";
        const direction = sorter.order === "ascend" ? "asc" : "desc";

        return sortKey ? `${sortKey}:${direction}` : "";
    }
}