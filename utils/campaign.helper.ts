
export class CampaignHelper {
    static toCampaignTableData(campaigns: Campaign[]): CampaignTableData[] {
        return campaigns.map(campaign => ({
            ...campaign,
            scenarioText: campaign.scenarios.map(scenario => scenario.name).join(','),
            creationTime: TimeframeHelper.toSpecificFormat(new Date(campaign.creation), 'dd/MM/yyyy, HH:mm:ss'),
            elapsedTime: TimeframeHelper.elapsedTime(new Date(campaign.creation), campaign.end ? new Date(campaign.end) : new Date()),
            statusTag: CampaignHelper.toExecutionStatusTag(campaign.status)
        }))
    }

    static toExecutionStatusTag(executionStatus: ExecutionStatus): Tag {
        switch (executionStatus) {
            case ExecutionStatus.SUCCESSFUL:
                return {
                    text: 'Successful',
                    textCssClass: 'text-green',
                    backgroundCssClass: 'bg-light-green'
                };
            case ExecutionStatus.FAILED:
                return {
                    text: 'Failed',
                    textCssClass: 'text-pink',
                    backgroundCssClass: 'bg-light-pink'
                };
            case ExecutionStatus.IN_PROGRESS:
                return {
                    text: 'In progress',
                    textCssClass: 'text-purple',
                    backgroundCssClass: 'bg-light-purple'
                };
            case ExecutionStatus.SCHEDULED:
                return {
                    text: 'Scheduled',
                    textCssClass: 'text-green',
                    backgroundCssClass: 'bg-grey-4'
                };
            case ExecutionStatus.WARNING:
                return {
                    text: 'Warning',
                    textCssClass: 'text-yellow',
                    backgroundCssClass: 'bg-yellow'
                };
            case ExecutionStatus.ABORTED:
                return {
                    text: 'Aborted',
                    textCssClass: 'text-pink',
                    backgroundCssClass: 'bg-light-pink'
                };
            case ExecutionStatus.QUEUED:
                return {
                    text: 'Queued',
                    textCssClass: 'text-purple',
                    backgroundCssClass: 'bg-light-purple'
                };
            default:
                return {
                    text: executionStatus,
                    textCssClass: 'text-grey',
                    backgroundCssClass: 'bg-grey-4'
                };
        }
    }

    static getTableColumnConfigs() {
        return [
            {
                title: 'Campaign',
                dataIndex: 'name',
                key: 'name',
                sorter: (next: CampaignTableData, prev: CampaignTableData) => next.name.localeCompare(prev.name),
            },
            {
                title: 'Scenario',
                dataIndex: 'scenarioText',
                key: 'scenarioText',
            },
            {
                title: 'Status',
                dataIndex: 'result',
                key: 'result',
                sorter: (next: CampaignTableData, prev: CampaignTableData) => next.status.localeCompare(prev.status),
            },
            {
                title: 'Created',
                dataIndex: 'creation',
                key: 'creation',
                sorter: (next: CampaignTableData, prev: CampaignTableData) => next.creation.localeCompare(prev.creation),
            },
            {
                title: 'Elapsed time',
                dataIndex: 'elapsedTime',
                key: 'elapsedTime'
            }
        ];
    }
}