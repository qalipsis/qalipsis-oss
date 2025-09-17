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

export interface ScenarioSummary {
    /**
     * Version of the Scenario.
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

    /**
     * List of directed acyclic graphs structuring the workflow of the scenario
     */
    directedAcyclicGraphs: DirectedAcyclicGraphSummary[];

    /**
     * Details of the execution profile to start the minions in the scenario
     */
    executionProfileConfiguration: { [key: string]: any };

    /**
     * Description of the Scenario, if any
     */
    description?: string;
}

export interface DirectedAcyclicGraphSummary {
    /**
     * The name of the directed acyclic graph, unique in a scenario
     */
    name: string;

    /**
     * Defines whether the DAG executes only singleton steps, such as poll steps for example
     */
    isSingleton: boolean;

    /**
     * Defines whether the DAG is linked to root of the scenario - true, or is following another DAG - false.
     */
    isRoot: boolean;

    /**
     * Defines whether the DAG executes minions under load, implying that its steps can be executed a massive count of times
     */
    isUnderLoad: boolean;

    /**
     * The number of actual - whether declared in the scenario or technically created by QALIPSIS - steps contains in the DAG
     */
    numberOfSteps: number;

    /**
     * Pairs of key/values that additionally describes the DAG
     */
    tags: { [key: string]: string };
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
    status: ExecutionStatus;

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

export const ReportMessageSeverityConstant = {
    /**
     * Severity for messages that have no impact on the final result and are just for user information.
     */
    INFO: 'INFO',

    /**
     * Severity for issues that have no impact on the final result but could potentially have negative side effect.
     */
    WARN: 'WARN',

    /**
     * Severity for issues that will let the campaign continue until the end but will make the campaign fail.
     */
    ERROR: 'ERROR',

    /**
     * Severity for issues that will immediately abort the campaign.
     */
    ABORT: 'ABORT'
} as const;

export type ReportMessageSeverity = typeof ReportMessageSeverityConstant[keyof typeof ReportMessageSeverityConstant];

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


export interface ScenarioRequest {
    /**
     * Counts of minions that will be assigned to the scenario, when not defined by the execution profile
     */
    minionsCount: number;
    /**
     * The configuration of the execution profile to execute a scenario
     */
    executionProfile: ExternalExecutionProfileConfiguration;
    /**
     * Distribution of the execution by zone
     */
    zones?: { [key: string]: number };
}


/**
 * The properties related to the scenario drawer.
 */
export interface ScenarioDrawer {
    /**
     * A flag to indicate if the drawer should be visible.
     */
    open: boolean;

    /**
     * The title of the drawer.
     */
    title: string;

    /**
     * The messages to be displayed in the drawer.
     */
    messages: ReportMessage[];
}

export interface ScenarioConfigurationForm {
    executionProfileStages: ExecutionProfileStage[];
    zones: ZoneForm[];
}

export const ExecutionProfileTypeConstant = {
    REGULAR: 'REGULAR',
    ACCELERATING: 'ACCELERATING',
    PROGRESSING_VOLUME: 'PROGRESSING_VOLUME',
    STAGE: 'STAGE',
    TIME_FRAME: 'TIME_FRAME'
} as const;

export type ExecutionProfileType = typeof ExecutionProfileTypeConstant[keyof typeof ExecutionProfileTypeConstant]

export type CompletionMode = 'GRACEFUL' | 'HARD';

export interface ExternalExecutionProfileConfiguration {
    profile: ExecutionProfileType
}

export interface ExecutionProfileStage {
    minionsCount: number;
    duration: number;
    startDuration: number;
    resolution: number;
}

export class StageExternalExecutionProfileConfiguration implements ExternalExecutionProfileConfiguration {
    profile: ExecutionProfileType;
    stages: Stage[];
    completion: CompletionMode;

    constructor(stages: Stage[], completion: CompletionMode) {
        this.profile = ExecutionProfileTypeConstant.STAGE;
        this.stages = stages;
        this.completion = completion;
    }
}

export interface Stage {
    /**
     * Total number of minions to start in the stage.
     */
    minionsCount: number;

    /**
     * Minions ramp up duration, in milliseconds.
     */
    rampUpDurationMs: number;

    /**
     * Stage duration, in milliseconds.
     */
    totalDurationMs: number;

    /**
     * Minimal duration between two triggering of minions start, default to 500 ms.
     */
    resolutionMs: number;
}

export interface ZoneForm {
    name: string;
    share: number;
}

export interface Zone {
    /**
     * A more detailed definition of the zone, generally the region, datacenter and the localization details
     */
    description: string;
    /**
     * A unique identifier for the zone
     */
    key: string;
    /**
     * A complete name of the zone, generally the country
     */
    title: string;
    /**
     * Image URL to display for the zone
     */
    imagePath: string;
    /**
     * A flag to indicate if the zone is enabled.
     */
    enabled: boolean;
}
