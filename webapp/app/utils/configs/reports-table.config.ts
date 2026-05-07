export const ReportsTableConfig = {
    TABLE_COLUMNS: [
        {
            title: 'Name',
            key: 'displayName',
            sortingEnabled: true,
        },
        {
            title: 'Description',
            key: 'description',
            sortingEnabled: true,
        },
        {
            title: 'Campaigns',
            key: 'concatenatedCampaignNames',
        },
    ] as TableColumnConfig[],
}
