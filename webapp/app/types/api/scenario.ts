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
    messages?: ReportMessage[];

    /**
     * Counts of scheduled minions for the scenario.
     */
    scheduledMinions?: number;

    /**
     * Execution details of each step within this scenario.
     */
    steps?: StepExecutionDetails[];

    /**
     * Aggregated meters produced by this scenario during the campaign execution, at the scenario level.
     */
    meters?: TimeSeriesMeter[];

    /**
     * Distribution of load across zones for this scenario, keyed by zone key with percentage values (0–100).
     */
    zoneDistribution?: { [key: string]: number };
}

/**
 * Execution details of a single step within a scenario.
 */
export interface StepExecutionDetails {
    /**
     * Name of the step as defined in the scenario.
     */
    name: string;

    /**
     * Technical type of the step, e.g. HTTP, JAVASCRIPT, KAFKA.
     */
    type?: string;

    /**
     * Counts of minions that started the execution of this step.
     */
    startedMinions?: number;

    /**
     * Counts of step executions that completed successfully.
     */
    successfulExecutions?: number;

    /**
     * Counts of step executions that failed.
     */
    failedExecutions?: number;

    /**
     * Overall execution status of the step.
     */
    status: ExecutionStatus;

    /**
     * Report messages produced by this step.
     */
    messages?: ReportMessage[];

    /**
     * Aggregated meters produced by this step during the campaign execution, at the step level.
     */
    meters?: TimeSeriesMeter[];

    /**
     * Total executions attempted on this step (successfulExecutions + failedExecutions).
     * Computed by the backend and included in the JSON response.
     */
    totalExecutions: number;

    /**
     * Whether the step was not executed at all (totalExecutions === 0).
     * Computed by the backend and included in the JSON response.
     */
    notExecuted: boolean;
}

/**
 * A time-based meter value produced during the execution of a campaign.
 */
export interface TimeSeriesMeter {
    /**
     * Name of the meter.
     */
    name: string;

    /**
     * Timestamp when the meter was recorded (ISO-8601 string).
     */
    timestamp: string;

    /**
     * Technical type of the meter (e.g. COUNTER, GAUGE, TIMER, DISTRIBUTION_SUMMARY).
     */
    type: string;

    /**
     * Additional tags attached to the meter.
     */
    tags?: { [key: string]: string };

    campaign?: string;
    scenario?: string;

    /**
     * Count of calls (counter meters).
     */
    count?: number;

    /**
     * Current value of a gauge.
     */
    value?: number;

    sum?: number;
    mean?: number;
    max?: number;
    activeTasks?: number;

    /**
     * Duration values serialized as ISO-8601 strings (e.g. "PT1.234S").
     */
    duration?: string;
    sumDuration?: string;
    meanDuration?: string;
    maxDuration?: string;

    other?: { [key: string]: number };
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
