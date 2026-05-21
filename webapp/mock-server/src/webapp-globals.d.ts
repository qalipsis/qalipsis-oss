// Webapp type files rely on Nuxt auto-imports (configured via nuxt.config.ts imports.dirs).
// In our standalone tsx context there is no Nuxt, so we re-declare the cross-referenced
// types as globals here. Keep this list in sync with cross-file references in app/types/api/.
declare global {
  type Scenario = import('@webapp-types/scenario').Scenario
  type ScenarioExecutionDetails = import('@webapp-types/scenario').ScenarioExecutionDetails
  type ScenarioRequest = import('@webapp-types/scenario').ScenarioRequest
  type TimeRange = import('@webapp-types/time-range').TimeRange
  type User = import('@webapp-types/user').User
  type Tenant = import('@webapp-types/tenant').Tenant
  type SharingMode = import('@webapp-types/series').SharingMode
  type DataSeries = import('@webapp-types/series').DataSeries
  type ExecutionStatus = import('@webapp-types/campaign').ExecutionStatus
  type PageQueryParams = import('@webapp-types/page').PageQueryParams
}

export {}
