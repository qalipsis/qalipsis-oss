export interface Report {
    /**
     * Identifier of the report
     */
    reference: string;
    
    /**
     * Last update of the report
     */
    version: string;

    /**
     * The report's creator username
     */
    creator: string;

    /**
     * Display name of the report, should be unique into a tenant
     */
    displayName: string;

    /**
     * Sharing mode with the other members of the tenant
     */
    sharingMode: SharingMode;

    /**
     * List of campaign keys to be included in the report
     */
    campaignKeys?: string[];

    /**
     * List of campaign names patterns to be included in the report
     */
    campaignNamesPatterns?: string[];

    /**
     * List of campaign keys to be included in the report and obtained based on campaign name pattern
     */
    resolvedCampaigns?: CampaignKeyAndName[];

    /**
     * List of scenario names patterns to be included in the report
     */
    scenarioNamesPatterns?: string[];

    /**
     * List of scenario names to be included in the report and obtained based on scenario name pattern
     */
    resolvedScenarioNames?: string[];

    dataComponents: DataComponent[];
}

export interface ReportTableData extends Report {
    /**
     * The concatenation of the campaign names.
     */
    concatenatedCampaignNames?: string;
}

export interface CampaignKeyAndName {
    /**
     * The key of the campaign
     */
    key: string;
    /**
     * The name of the campaign
     */
    name: string;
}

/**
 * The type of data component to include in the report.
 */
export enum DataComponentType {
    DIAGRAM = 'DIAGRAM',
    DATA_TABLE = 'DATA_TABLE'
}

export interface DataComponent {
    /**
     * The data series
     */
    datas: DataSeries[];
    /**
     * The type of the data component
     */
    type: DataComponentType;
}

export interface ReportCreationAndUpdateRequest {
    /**
     * Display name of the report, should be unique into a tenant
     */
    displayName: string;

    /**
     * Sharing mode with the other members of the tenant
     */
    sharingMode?: SharingMode;

    /**
     * List of keys that represent campaigns to include in the report
     */
    campaignKeys?: string[];

    /**
     * List of keys that represent campaigns to include in the report
     */
    campaignNamesPatterns?: string[];

    /**
     * List of scenarios to include in the report
     */
    scenarioNamesPatterns?: string[];

    /**
     * List of data component to include in the report
     */
    dataComponents?: DataComponentCreationAndUpdateRequest[];
}

export interface DataComponentCreationAndUpdateRequest {
    /**
     * The references of the data series.
     */
    dataSeriesReferences: string[];
    /**
     * The type of the data component.
     */
    type: DataComponentType;
}