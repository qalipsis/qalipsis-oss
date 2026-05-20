import { DateTime } from 'luxon'
import type { Campaign, CampaignConfiguration, CampaignExecutionDetails, ExecutionStatus } from '@webapp-types/campaign'
import type { DataSeries, DataType, SharingMode, QueryAggregationOperator } from '@webapp-types/series'
import type { DataReport, DataComponentType } from '@webapp-types/report'
import type { ScenarioSummary } from '@webapp-types/scenario'
import type { Zone } from '@webapp-types/zone'
import type { Profile } from '@webapp-types/profile'
import type { PermissionEnum } from '@webapp-types/permission'
import type { DefaultCampaignConfiguration } from '@webapp-types/campaign-configuration'
import { db } from './db.js'

const now = DateTime.utc()
const iso = (dt: DateTime) => dt.toISO()!

const PERMISSIONS: PermissionEnum[] = [
  'read:series',
  'write:series',
  'read:campaign',
  'write:campaign',
  'abort:campaign',
  'create:campaign',
  'read:report',
  'write:report',
]

const ZONES: Zone[] = [
  {
    key: 'eu-west',
    title: 'Europe West',
    description: 'Frankfurt, Germany',
    imagePath: 'https://flagcdn.com/de.svg',
    enabled: true,
  },
  {
    key: 'us-east',
    title: 'United States East',
    description: 'Virginia, USA',
    imagePath: 'https://flagcdn.com/us.svg',
    enabled: true,
  },
]

const buildScenarios = (): ScenarioSummary[] => [
  {
    version: iso(now.minus({ days: 5 })),
    name: 'checkout-flow',
    minionsCount: 200,
    description: 'End-to-end checkout under load',
    directedAcyclicGraphs: [
      {
        name: 'root-dag',
        isSingleton: false,
        isRoot: true,
        isUnderLoad: true,
        numberOfSteps: 12,
        tags: { area: 'checkout' },
      },
    ],
    executionProfileConfiguration: { profile: 'REGULAR' },
  },
  {
    version: iso(now.minus({ days: 3 })),
    name: 'search-latency',
    minionsCount: 500,
    description: 'Search endpoint latency probe',
    directedAcyclicGraphs: [
      {
        name: 'root-dag',
        isSingleton: false,
        isRoot: true,
        isUnderLoad: true,
        numberOfSteps: 6,
        tags: { area: 'search' },
      },
    ],
    executionProfileConfiguration: { profile: 'ACCELERATING' },
  },
  {
    version: iso(now.minus({ days: 1 })),
    name: 'auth-burst',
    minionsCount: 1000,
    description: 'Bursty login traffic',
    directedAcyclicGraphs: [
      {
        name: 'root-dag',
        isSingleton: false,
        isRoot: true,
        isUnderLoad: true,
        numberOfSteps: 4,
        tags: { area: 'auth' },
      },
    ],
    executionProfileConfiguration: { profile: 'STAGE' },
  },
]

const buildCampaigns = () => {
  const items: Array<{
    key: string
    name: string
    status: ExecutionStatus
    minutesAgo: number
    durationMin: number
    scheduled?: boolean
  }> = [
    { key: 'campaign-running', name: 'Nightly checkout regression', status: 'IN_PROGRESS', minutesAgo: 12, durationMin: 0 },
    { key: 'campaign-ok-1', name: 'Search latency sweep', status: 'SUCCESSFUL', minutesAgo: 240, durationMin: 35 },
    { key: 'campaign-ok-2', name: 'Auth burst weekly', status: 'SUCCESSFUL', minutesAgo: 1440, durationMin: 22 },
    { key: 'campaign-failed', name: 'Catalog stress test', status: 'FAILED', minutesAgo: 720, durationMin: 14 },
    { key: 'campaign-scheduled', name: 'Black Friday rehearsal', status: 'SCHEDULED', minutesAgo: -120, durationMin: 60, scheduled: true },
  ]

  for (const it of items) {
    const start = now.minus({ minutes: it.minutesAgo })
    const end = it.status === 'IN_PROGRESS' || it.scheduled ? undefined : start.plus({ minutes: it.durationMin })

    const summary: Campaign = {
      version: iso(start),
      key: it.key,
      creation: iso(start.minus({ minutes: 5 })),
      name: it.name,
      speedFactor: 1,
      scheduledMinions: 1000,
      start: it.scheduled ? undefined : iso(start),
      end: end ? iso(end) : undefined,
      status: it.status,
      configurerName: 'qalipsis-dev',
      scenarios: db.scenarios.map((s) => ({ version: s.version, name: s.name, minionsCount: s.minionsCount })),
      zones: 'eu-west',
    }
    db.campaigns.set(it.key, summary)

    const details: CampaignExecutionDetails = {
      version: summary.version,
      key: summary.key,
      creation: summary.creation,
      name: summary.name,
      speedFactor: summary.speedFactor,
      scheduledMinions: summary.scheduledMinions,
      start: summary.start,
      end: summary.end,
      status: summary.status!,
      configurerName: summary.configurerName,
      scenarios: summary.scenarios,
      zones: ['eu-west'],
      startedMinions: it.status === 'SCHEDULED' ? 0 : 1000,
      completedMinions: it.status === 'IN_PROGRESS' || it.status === 'SCHEDULED' ? 0 : 985,
      successfulExecutions: it.status === 'FAILED' ? 8200 : 12450,
      failedExecutions: it.status === 'FAILED' ? 350 : 17,
      scenariosReports: db.scenarios.map((s) => ({
        id: `${it.key}::${s.name}`,
        name: s.name,
        start: summary.start,
        end: summary.end,
        startedMinions: 500,
        completedMinions: 495,
        successfulExecutions: 6200,
        failedExecutions: 5,
        status: it.status === 'FAILED' ? 'FAILED' : it.status,
        messages: it.status === 'FAILED' ? [
          {
            stepName: 'http-post-/checkout',
            messageId: 'msg-1',
            severity: 'ERROR',
            message: 'Response time exceeded SLA threshold (>2s) on 4.3% of requests',
          },
        ] : [],
      })),
    }
    db.campaignDetails.set(it.key, details)

    const config: CampaignConfiguration = {
      name: it.name,
      speedFactor: 1,
      startOffsetMs: 1000,
      timeout: 'PT1H',
      hardTimeout: false,
      scenarios: Object.fromEntries(
        db.scenarios.map((s) => [s.name, {
          minionsCount: s.minionsCount,
          executionProfile: { profile: 'REGULAR' },
          zones: { 'eu-west': 100 },
        }]),
      ),
      scheduledAt: it.scheduled ? iso(now.plus({ minutes: 120 })) : undefined,
    }
    db.campaignConfigurations.set(it.key, config)
  }
}

const buildDataSeries = () => {
  const features: Array<{ label: string; valuePrefix: string }> = [
    { label: 'Checkout', valuePrefix: 'checkout' },
    { label: 'Search', valuePrefix: 'search' },
    { label: 'Login', valuePrefix: 'auth.login' },
    { label: 'Signup', valuePrefix: 'auth.signup' },
    { label: 'Cart', valuePrefix: 'cart' },
    { label: 'Product', valuePrefix: 'product' },
    { label: 'Order', valuePrefix: 'order' },
    { label: 'Payment', valuePrefix: 'payment' },
    { label: 'Inventory', valuePrefix: 'inventory' },
    { label: 'Cache', valuePrefix: 'cache' },
    { label: 'DB query', valuePrefix: 'db.query' },
    { label: 'Notification', valuePrefix: 'notification' },
    { label: 'Profile', valuePrefix: 'profile' },
  ]
  const metrics: Array<{
    suffix: string
    valueSuffix: string
    dataType: DataType
    aggregation: QueryAggregationOperator
  }> = [
    { suffix: 'requests', valueSuffix: 'requests', dataType: 'METERS', aggregation: 'COUNT' },
    { suffix: 'latency avg', valueSuffix: 'duration', dataType: 'METERS', aggregation: 'AVERAGE' },
    { suffix: 'latency p99', valueSuffix: 'duration', dataType: 'METERS', aggregation: 'PERCENTILE_99' },
    { suffix: 'errors', valueSuffix: 'errors', dataType: 'EVENTS', aggregation: 'COUNT' },
  ]
  const palette = [
    '#3B82F6', '#EF4444', '#F59E0B', '#10B981', '#06B6D4', '#8B5CF6',
    '#EC4899', '#22C55E', '#EAB308', '#6366F1', '#84CC16', '#F97316',
  ]

  let idx = 0
  for (const f of features) {
    for (const m of metrics) {
      const reference = `ds-${String(idx + 1).padStart(3, '0')}`
      const item: DataSeries = {
        reference,
        version: iso(now.minus({ days: idx })),
        creator: 'qalipsis-dev',
        displayName: `${f.label} ${m.suffix}`,
        valueName: `${f.valuePrefix}.${m.valueSuffix}`,
        dataType: m.dataType,
        sharingMode: 'WRITE' as SharingMode,
        color: palette[idx % palette.length],
        filters: [],
        aggregationOperation: m.aggregation,
        timeframeUnit: 'PT10S',
      }
      db.dataSeries.set(reference, item)
      idx++
    }
  }
}

const buildReports = () => {
  const refs = Array.from(db.dataSeries.values()).slice(0, 3)
  const reportA: DataReport = {
    reference: 'report-001',
    version: iso(now.minus({ days: 2 })),
    creator: 'qalipsis-dev',
    displayName: 'Checkout regression — weekly',
    description: 'Tracks checkout latency and throughput over the last 7 days',
    sharingMode: 'WRITE' as SharingMode,
    campaignKeys: ['campaign-ok-1', 'campaign-ok-2'],
    resolvedCampaigns: [
      { key: 'campaign-ok-1', name: 'Search latency sweep' },
      { key: 'campaign-ok-2', name: 'Auth burst weekly' },
    ],
    scenarioNamesPatterns: ['*'],
    resolvedScenarioNames: db.scenarios.map((s) => s.name),
    dataComponents: [
      { type: 'DIAGRAM' as DataComponentType, datas: refs.slice(0, 2) },
      { type: 'DATA_TABLE' as DataComponentType, datas: refs },
    ],
  }
  const reportB: DataReport = {
    reference: 'report-002',
    version: iso(now.minus({ hours: 6 })),
    creator: 'qalipsis-dev',
    displayName: 'Empty report (draft)',
    sharingMode: 'NONE' as SharingMode,
    dataComponents: [],
  }
  db.reports.set(reportA.reference, reportA)
  db.reports.set(reportB.reference, reportB)
}

const buildProfile = (): Profile => ({
  user: {
    tenant: 'mock-tenant',
    username: 'qalipsis-dev',
    blocked: false,
    creation: iso(now.minus({ months: 6 })),
    displayName: 'QALIPSIS Dev',
    email: 'dev@qalipsis.local',
    emailVerified: true,
    familyName: 'Dev',
    givenName: 'QALIPSIS',
    roles: ['SUPER_ADMINISTRATOR', 'TENANT_ADMINISTRATOR'],
    status: 'ACTIVE',
    version: iso(now),
  },
  tenants: [{ reference: 'mock-tenant', displayName: 'Mock Tenant' }],
})

const buildDefaultCampaignConfig = (): DefaultCampaignConfiguration => ({
  validation: {
    maxMinionsCount: 100000,
    maxExecutionDuration: 'PT4H',
    maxScenariosCount: 20,
    stage: {
      minMinionsCount: 1,
      maxMinionsCount: 100000,
      minResolution: 'PT0.5S',
      maxResolution: 'PT1M',
      minDuration: 'PT5S',
      maxDuration: 'PT1H',
      minStartDuration: 'PT0S',
      maxStartDuration: 'PT10M',
    },
  },
})

export const seed = () => {
  db.permissions = PERMISSIONS
  db.zones = ZONES
  db.scenarios = buildScenarios()
  db.profile = buildProfile()
  db.defaultCampaignConfiguration = buildDefaultCampaignConfig()
  buildDataSeries()
  buildCampaigns()
  buildReports()
}
