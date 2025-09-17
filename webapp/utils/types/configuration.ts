export interface DefaultCampaignConfiguration {
    validation: Validation;
}

export interface Validation {
    maxMinionsCount: number;
    maxExecutionDuration: string;
    maxScenariosCount: number;
    stage: StageValidation;
}

export interface StageValidation {
    minMinionsCount: number;
    maxMinionsCount: number;
    minResolution: string;
    maxResolution: string;
    minDuration: string;
    maxDuration: string;
    minStartDuration: string;
    maxStartDuration: string;
}
