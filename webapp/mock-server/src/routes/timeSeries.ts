import { Router } from 'express'
import { DateTime, Duration } from 'luxon'
import type { CampaignSummaryResult } from '@webapp-types/report'
import { generateAggregation } from '../timeSeriesGenerator.js'

export const timeSeriesRouter = Router()

const splitCsv = (v: unknown): string[] =>
  typeof v === 'string' ? v.split(',').map((s) => s.trim()).filter(Boolean) : []

timeSeriesRouter.get('/aggregate', (req, res) => {
  const series = splitCsv(req.query.series)
  const campaigns = splitCsv(req.query.campaigns)
  const result = generateAggregation({
    series,
    campaigns: campaigns.length > 0 ? campaigns : ['mock-campaign'],
    from: typeof req.query.from === 'string' ? req.query.from : undefined,
    until: typeof req.query.until === 'string' ? req.query.until : undefined,
    timeframe: typeof req.query.timeframe === 'string' ? req.query.timeframe : undefined,
  })
  res.json(result)
})

timeSeriesRouter.get('/summary/campaign-status', (req, res) => {
  const from = typeof req.query.from === 'string' ? DateTime.fromISO(req.query.from) : DateTime.utc().minus({ days: 7 })
  const until = typeof req.query.until === 'string' ? DateTime.fromISO(req.query.until) : DateTime.utc()
  const timeframe = typeof req.query.timeframe === 'string'
    ? Duration.fromISO(req.query.timeframe)
    : Duration.fromObject({ hours: 6 })

  if (!from.isValid || !until.isValid || until <= from) {
    res.json([])

    return
  }

  const buckets: CampaignSummaryResult[] = []
  const step = timeframe.as('milliseconds')
  let t = from.toMillis()
  let i = 0
  while (t < until.toMillis()) {
    const successful = 3 + ((i * 17) % 8)
    const failed = i % 5 === 0 ? 1 : 0
    buckets.push({ start: DateTime.fromMillis(t).toISO()!, successful, failed })
    t += step
    i++
  }
  res.json(buckets)
})
