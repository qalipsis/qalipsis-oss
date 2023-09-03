export class ReportHelper {
    static toReportTableData(reports: Report[]): ReportTableData[] {
        return reports.map(report => ({
            ...report,
            concatenatedCampaignNames: report.resolvedCampaigns?.map(campaign => campaign.name)?.join(',')
        }))
    }

    // TODO:
    static toReportTableComponentData() {

    }

    // TODO:
    static toReportChartComponentData() {

    }

    static getTableColumnConfigs() {
        return [
            {
                title: 'Name',
                dataIndex: 'displayName',
                key: 'displayName',
                sorter: (next: Report, prev: Report) => next.displayName.localeCompare(prev.displayName),
            },
            {
                title: 'Campaigns',
                dataIndex: 'concatenatedCampaignNames',
                key: 'concatenatedCampaignNames',
            },
            {
                title: 'Author',
                dataIndex: 'creator',
                key: 'creator',
                sorter: (next: Report, prev: Report) => next.creator.localeCompare(prev.creator),
            },
            {
                title: '',
                dataIndex: 'actions',
                key: 'actions',
            },
        ];
    }
}