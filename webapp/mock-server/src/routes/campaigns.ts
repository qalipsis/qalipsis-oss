import { Router } from 'express'
import { DateTime } from 'luxon'
import type { Campaign, CampaignConfiguration, CampaignExecutionDetails } from '@webapp-types/campaign'
import type { PageQueryParams } from '@webapp-types/page'
import { db } from '../db.js'
import { paginate } from '../paginate.js'

export const campaignsRouter = Router()

const numberOrUndefined = (v: unknown): number | undefined => {
  if (v == null) return undefined
  const n = Number(v)

  return Number.isFinite(n) ? n : undefined
}

const toPageQuery = (q: Record<string, unknown>): PageQueryParams => ({
  filter: typeof q.filter === 'string' ? q.filter : undefined,
  sort: typeof q.sort === 'string' ? q.sort : undefined,
  page: numberOrUndefined(q.page),
  size: numberOrUndefined(q.size),
})

campaignsRouter.get('/', (req, res) => {
  const page = paginate(Array.from(db.campaigns.values()), toPageQuery(req.query), {
    filterFields: ['name', 'key'],
  })
  res.json(page)
})

campaignsRouter.post('/', (req, res) => {
  const config = req.body as CampaignConfiguration
  const key = `campaign-${Date.now()}`
  const nowIso = new Date().toISOString()
  const summary: Campaign = {
    version: nowIso,
    key,
    creation: nowIso,
    name: config.name,
    speedFactor: config.speedFactor ?? 1,
    scheduledMinions: 0,
    start: nowIso,
    status: 'IN_PROGRESS',
    configurerName: 'qalipsis-dev',
    scenarios: [],
  }
  db.campaigns.set(key, summary)
  db.campaignConfigurations.set(key, config)
  db.campaignDetails.set(key, {
    version: summary.version,
    key: summary.key,
    creation: summary.creation,
    name: summary.name,
    speedFactor: summary.speedFactor,
    scheduledMinions: summary.scheduledMinions,
    start: summary.start,
    end: summary.end,
    status: 'IN_PROGRESS',
    configurerName: summary.configurerName,
    scenarios: summary.scenarios,
    zones: [],
    startedMinions: 0,
    completedMinions: 0,
    successfulExecutions: 0,
    failedExecutions: 0,
    scenariosReports: [],
  })
  res.json(summary)
})

campaignsRouter.post('/schedule', (req, res) => {
  const config = req.body as CampaignConfiguration
  const key = `campaign-sched-${Date.now()}`
  const nowIso = new Date().toISOString()
  const summary: Campaign = {
    version: nowIso,
    key,
    creation: nowIso,
    name: config.name,
    speedFactor: config.speedFactor ?? 1,
    status: 'SCHEDULED',
    configurerName: 'qalipsis-dev',
    scenarios: [],
  }
  db.campaigns.set(key, summary)
  db.campaignConfigurations.set(key, config)
  res.json(summary)
})

campaignsRouter.put('/schedule/:key', (req, res) => {
  const { key } = req.params
  const existing = db.campaigns.get(key)
  if (!existing) {
    res.status(404).json({ message: `Campaign ${key} not found` })

    return
  }
  const config = req.body as CampaignConfiguration
  const updated: Campaign = {
    ...existing,
    name: config.name ?? existing.name,
    speedFactor: config.speedFactor ?? existing.speedFactor,
    version: new Date().toISOString(),
  }
  db.campaigns.set(key, updated)
  db.campaignConfigurations.set(key, config)
  res.json(updated)
})

campaignsRouter.get('/:key/configuration', (req, res) => {
  const config = db.campaignConfigurations.get(req.params.key)
  if (!config) {
    res.status(404).json({ message: `Configuration for campaign ${req.params.key} not found` })

    return
  }
  res.json(config)
})

campaignsRouter.post('/:key/abort', (req, res) => {
  const details = db.campaignDetails.get(req.params.key)
  const summary = db.campaigns.get(req.params.key)
  if (!details || !summary) {
    res.status(404).json({ message: `Campaign ${req.params.key} not found` })

    return
  }
  const hard = Boolean((req.body as { hard?: boolean } | undefined)?.hard)
  const endIso = new Date().toISOString()
  const status = hard ? 'FAILED' : 'ABORTED'
  details.status = status
  details.end = endIso
  details.aborterName = 'qalipsis-dev'
  summary.status = status
  summary.end = endIso
  summary.aborterName = 'qalipsis-dev'
  res.json(details)
})

campaignsRouter.post('/:key/replay', (req, res) => {
  const original = db.campaignDetails.get(req.params.key)
  if (!original) {
    res.status(404).json({ message: `Campaign ${req.params.key} not found` })

    return
  }
  const newKey = `${req.params.key}-replay-${Date.now()}`
  const nowIso = new Date().toISOString()
  const replay: CampaignExecutionDetails = {
    ...original,
    key: newKey,
    creation: nowIso,
    version: nowIso,
    start: nowIso,
    end: undefined,
    status: 'IN_PROGRESS',
    startedMinions: 0,
    completedMinions: 0,
    successfulExecutions: 0,
    failedExecutions: 0,
  }
  db.campaignDetails.set(newKey, replay)
  const summary: Campaign = {
    version: nowIso,
    key: newKey,
    creation: nowIso,
    name: `Replay of ${original.name}`,
    speedFactor: original.speedFactor,
    start: nowIso,
    status: 'IN_PROGRESS',
    configurerName: 'qalipsis-dev',
    scenarios: original.scenarios ?? [],
  }
  db.campaigns.set(newKey, summary)
  res.json(replay)
})

// Comma-separated path param: /campaigns/ref1,ref2,ref3 → CampaignExecutionDetails[]
// Must be last to avoid shadowing /:key/configuration etc.
campaignsRouter.get('/:refs', (req, res) => {
  const refs = req.params.refs.split(',').map((r) => r.trim()).filter(Boolean)
  const found = refs.map((r) => db.campaignDetails.get(r)).filter((c): c is CampaignExecutionDetails => !!c)
  // Refresh IN_PROGRESS campaign timestamps so the chart keeps moving
  for (const c of found) {
    if (c.status === 'IN_PROGRESS') {
      c.version = DateTime.utc().toISO()!
    }
  }
  res.json(found)
})
