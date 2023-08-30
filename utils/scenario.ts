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
 * Details for the scenario report to retrieve from the REST endpoint.
 */
export interface ScenarioExecutionDetails {
    /**
     * Identifier of the scenario.
     */
    id: string;

    /**
     * Display name of the scenario.
     */
    name: string;

    /**
     * Date and time when the scenario started.
     */
    start?: string;

    /**
     * Date and time when the scenario was completed, whether successfully or not.
     */
    end?: string;

    /**
     * Counts of minions when the scenario started.
     */
    startedMinions?: number;

    /**
     * Counts of minions that completed their scenario.
     */
    completedMinions?: number,

    /**
     * Counts of minions that successfully completed their scenario.
     */
    successfulExecutions?: number;

    /**
     * Counts of minions that failed to execute their scenario.
     */
    failedExecutions?: number;

    /**
     * Overall execution status of the scenario.
     */
    status: ExecutionStatus,

    /**
     * The list of the report messages for the scenario
     */
    messages: ReportMessage[];
}

/**
 * Details for the scenario report message to retrieve from the REST endpoint
 */
export interface ReportMessage {
    /**
     * Identifier of the step.
     */
    stepName: string;

    /**
     * Identifier of the message
     */
    messageId: string;

    /**
     * Severity of the report message
     */
    severity: ReportMessageSeverity;

    /**
     * The message itself
     */
    message: string;

    /**
     * The tag of the severity
     * @remark 
     * This property is used for displaying the tag on the table
     */
    severityTag?: Tag;
}

export enum ReportMessageSeverity {
    /**
     * Severity for messages that have no impact on the final result and are just for user information.
     */
    INFO = 'INFO',

    /**
     * Severity for issues that have no impact on the final result but could potentially have negative side effect.
     */
    WARN = 'WARN',

    /**
     * Severity for issues that will let the campaign continue until the end but will make the campaign fail.
     */
    ERROR = 'ERROR',

    /**
     * Severity for issues that will immediately abort the campaign.
     */
    ABORT = 'ABORT'
}

/**
 * The properties for the scenario dropdown option.
 */
export interface ScenarioOption {
    /**
     * The value of the scenario option. The value is the same as the scenario name.
     */
    value: string;

    /**
     * The label of the scenario option. The scenario name is also used as the label.
     */
    label: string;

    /**
     * A flag to indicate if the scenario is currently selected
     */
    isActive: boolean;
}

/**
 * The report of scenario to display on the UI  
 */
export interface ScenarioReport {
    /**
     * Identifier of the scenario.
     */
    id: string;

    /**
     * Name of the scenario.
     */
    name: string;

    /**
     * Overall execution status of the scenario.
     */
    status: ExecutionStatus;

    /**
     * Date and time when the scenario started.
     */
    start?: string;

    /**
     * Date and time when the scenario was completed, whether successfully or not.
     */
    end?: string;

    /**
     * Counts of scheduled minions
     * 
     * @Remark
     * For the scenario: the number will be the same as the scheduledMinions
     * For the summary scenario: the number will be the scheduledMinions from the CampaignExecutionDetails.
     */
    scheduledMinions: number;

    /**
     * Counts of minions when the scenario started.
     */
    startedMinions?: number;

    /**
     * Counts of minions that completed their scenario.
     */
    completedMinions?: number;

    /**
     * Counts of minions that successfully completed their scenario.
     */
    successfulExecutions?: number;

    /**
     * Counts of minions that failed to execute their scenario.
     */
    failedExecutions?: number;

    /**
     * The failure reason of the campaign.
     * 
     * @Remark
     * Only used by the summary scenario.
     */
    failureReason?: string;

    /**
     * The list of the report messages for the scenario
     */
    messages: ReportMessage[];
}
