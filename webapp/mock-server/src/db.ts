import type { Campaign, CampaignConfiguration, CampaignExecutionDetails } from '@webapp-types/campaign'
import type { DataSeries } from '@webapp-types/series'
import type { DataReport } from '@webapp-types/report'
import type { ScenarioSummary } from '@webapp-types/scenario'
import type { Zone } from '@webapp-types/zone'
import type { Profile } from '@webapp-types/profile'
import type { PermissionEnum } from '@webapp-types/permission'
import type { DefaultCampaignConfiguration } from '@webapp-types/campaign-configuration'

export interface ReportTaskState {
  reference: string
  reportReference: string
  readyAt: number
  creationTimestamp: string
  payload?: Buffer
}

interface DB {
  campaigns: Map<string, Campaign>
  campaignDetails: Map<string, CampaignExecutionDetails>
  campaignConfigurations: Map<string, CampaignConfiguration>
  dataSeries: Map<string, DataSeries>
  reports: Map<string, DataReport>
  reportTasks: Map<string, ReportTaskState>
  scenarios: ScenarioSummary[]
  zones: Zone[]
  profile: Profile
  permissions: PermissionEnum[]
  defaultCampaignConfiguration: DefaultCampaignConfiguration
}

export const db: DB = {
  campaigns: new Map(),
  campaignDetails: new Map(),
  campaignConfigurations: new Map(),
  dataSeries: new Map(),
  reports: new Map(),
  reportTasks: new Map(),
  scenarios: [],
  zones: [],
  profile: { user: {} as Profile['user'], tenants: [] },
  permissions: [],
  defaultCampaignConfiguration: {} as DefaultCampaignConfiguration,
}
