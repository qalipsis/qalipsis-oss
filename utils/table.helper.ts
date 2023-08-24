export class TableHelper {
    static sharedPaginationProperties = {
        showQuickJumper: true,
        position: ['bottomLeft'],
        showTotal(total: number, range: [number, number]): string {
            return `Showing ${range[0]} - ${range[1]} of ${total}`;
        },
        locale: { jump_to: "Go to page" }
    }

    static getCurrentPageIndex = (pagination: TablePaginationConfig) => {
        return pagination.current - 1;
    }

    static getSort = (sorter: SorterResult) => {
        const sortKey = sorter.column ? sorter.column.key : "";
        const direction = sorter.order === "ascend" ? "asc" : "desc";

        return sortKey ? `${sortKey}:${direction}` : "";
    }
}