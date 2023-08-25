export interface Campaign {
    /**
     * Last change of the campaign
     */
    version: string;

    /**
     * Unique identifier of the campaign
     */
    key: string;

    /**
     * Creation time of the campaign in ISO string format
     */
    creation: string;

    /**
     * Display name of the campaign
     */
    name: string;

    /**
     * Speed factor to apply on the execution profile, each strategy will apply it differently 
     * depending on its own implementation
     */
    speedFactor: number;

    /**
     * Counts of minions scheduled to be started
     */
    scheduledMinions?: number;

    /**
     * Instant when the campaign should be aborted without generating a failure in ISO string format
     */
    softTimeout?: string;

    /**
     * Instant when the campaign should be aborted as well as generating a failure
     */
    hardTimeout?: string;

    /**
     * Date and time when the campaign started
     */
    start?: string;

    /**
     * Date and time when the campaign was completed, whether successfully or not
     */
    end?: string;

    /**
     * Overall execution status of the campaign when completed
     */
    status?: ExecutionStatus;

    /**
     * The root cause of the campaign failure
     */
    failureReason?: string;

    /**
     * Name of the user, who created the campaign
     */
    configurerName?: string;

    /**
     * Name of the user, who aborted the campaign
     */
    aborterName?: string;

    /**
     * Scenarios being part of the campaign
     */
    scenarios: Scenario[];

    /**
     * Keys of the zones where the campaign was executed
     */
    zones?: string;
}

export interface CampaignTableData extends Campaign {
    /**
     * The elapsed time of the campaign.
     */
    elapsedTime: string;

    /**
     * Creation time of the campaign ih formatted text.
     */
    creationTime: string;

    /**
     * The text of all scenario names.
     */
    scenarioText: string;
    
    /**
     * The tag of the status.
     */
    statusTag: Tag;
}

/**
 * Details of the execution of a completed or running campaign and its scenario
 */
export interface Scenario {
    /**
     * Last stored update of the scenario.
     */
    version: string;

    /**
     * Display name of the scenario.
     */
    name: string;

    /**
     * Number of minions executed in the scenario.
     */
    minionsCount: number;
}

/**
 * Execution status of a ScenarioReport or CampaignReport.
 */
export enum ExecutionStatus {
    /**
     * SUCCESSFUL - all the steps, were successful
     */
    SUCCESSFUL = 'SUCCESSFUL',

    /**
     * WARNING - a deeper look at the reports is required, but the campaign does not fail
     */
    WARNING = 'WARNING',

    /**
     * FAILED - the campaign went until the end, but got errors
     */
    FAILED = 'FAILED',

    /**
     * ABORTED - the campaign was aborted, either by a user or a critical failure
     */
    ABORTED = 'ABORTED',

    /**
     * SCHEDULED - the campaign is scheduled for a later point in time
     */
    SCHEDULED = 'SCHEDULED',

    /**
     * QUEUED - the campaign is being prepared and will start very soon
     */
    QUEUED = 'QUEUED',

    /**
     * IN_PROGRESS - the campaign is currently running
     */
    IN_PROGRESS = 'IN_PROGRESS'
}