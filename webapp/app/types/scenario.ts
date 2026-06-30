/**
 * ReportMessage enriched with a FE-computed display tag for the severity.
 */
export interface ReportMessageDisplay extends ReportMessage {
    /**
     * The tag of the severity, computed by the FE for display purposes.
     */
    severityTag?: Tag;
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
     * @remarks
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
     * @remarks
     * Only used by the summary scenario.
     */
    failureReason?: string;

    /**
     * The list of the report messages for the scenario
     */
    messages: ReportMessageDisplay[];

    /**
     * Execution details of each step within this scenario.
     */
    steps: StepExecutionDetails[];

    /**
     * Aggregated meters produced by this scenario during the campaign execution.
     */
    meters: TimeSeriesMeter[];

    /**
     * Distribution of load across zones for this scenario (zone key → percentage 0–100).
     */
    zoneDistribution: { [key: string]: number };
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
    messages: ReportMessageDisplay[];
}

export interface ScenarioConfigurationForm {
    executionProfileStages: ExecutionProfileStage[];
    zones: ZoneForm[];
}

export interface ExecutionProfileStage {
    minionsCount: number;
    duration: number;
    durationUnit?: TimeframeUnit;
    rampUpDuration: number;
    rampUpDurationUnit?: TimeframeUnit;
    resolution: number;
}

export interface ZoneForm {
    name: string;
    share: number;
}
