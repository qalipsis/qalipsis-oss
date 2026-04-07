export const SeriesTableConfig = {
    TABLE_COLUMNS: [
        {
            title: "Name",
            key: "displayName",
            sortingEnabled: true,
        },
        {
            title: "Time-series type",
            key: "dataType",
            sortingEnabled: true,
        },
        {
            title: "Field name",
            key: "fieldName",
        },
        {
            title: "Value",
            key: "valueName",
        },
        {
            title: "Shared",
            key: "sharingMode",
            sortingEnabled: true,
        },
    ] as TableColumnConfig[],
}
