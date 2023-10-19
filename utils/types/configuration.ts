export interface DefaultCampaignConfiguration {
    validation: Validation;
}

export interface Validation {
    maxMinionsCount: number;
    maxExecutionDuration: number;
    maxScenariosCount: number;
    stage: StageValidation;
}

export interface StageValidation {
    minMinionsCount: number;
    maxMinionsCount: number;
    minResolution: number;
    maxResolution: number;
    minDuration: number;
    maxDuration: number;
    minStartDuration: number;
    maxStartDuration: number;
}
