import { DateTime, Duration } from 'luxon'
import type { TimeSeriesAggregationResult } from '@webapp-types/time-series'

const hashString = (s: string): number => {
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }

  return h >>> 0
}

const seriesParams = (seriesRef: string) => {
  const h = hashString(seriesRef)
  const base = 100 + (h % 400)
  const amplitude = 50 + ((h >>> 8) % 300)
  const periodMs = (60 + ((h >>> 16) % 240)) * 1000
  const jitterScale = 10 + ((h >>> 24) % 40)

  return { base, amplitude, periodMs, jitterScale }
}

export interface AggregationArgs {
  series: string[]
  campaigns: string[]
  from?: string
  until?: string
  timeframe?: string
}

export const generateAggregation = (args: AggregationArgs): Record<string, TimeSeriesAggregationResult[]> => {
  const until = args.until ? DateTime.fromISO(args.until) : DateTime.utc()
  const from = args.from ? DateTime.fromISO(args.from) : until.minus({ hours: 1 })
  const bucket = args.timeframe ? Duration.fromISO(args.timeframe) : Duration.fromObject({ seconds: 30 })
  const bucketMs = bucket.as('milliseconds')

  if (bucketMs <= 0 || !from.isValid || !until.isValid || until <= from) {
    return Object.fromEntries(args.series.map((s) => [s, []]))
  }

  const result: Record<string, TimeSeriesAggregationResult[]> = {}

  for (const seriesRef of args.series) {
    const { base, amplitude, periodMs, jitterScale } = seriesParams(seriesRef)
    const points: TimeSeriesAggregationResult[] = []
    const seed = hashString(seriesRef)

    for (const campaignKey of args.campaigns) {
      const campaignOffset = hashString(campaignKey) % periodMs
      let t = from.toMillis()
      let i = 0
      while (t < until.toMillis()) {
        const bucketStart = DateTime.fromMillis(t)
        const phase = (t + campaignOffset) / periodMs
        const jitter = (((seed + i * 2654435761) >>> 0) % 1000) / 1000 - 0.5
        const value = Math.max(0, base + amplitude * Math.sin(phase * 2 * Math.PI) + jitter * jitterScale)
        points.push({
          start: bucketStart.toISO()!,
          elapsed: Duration.fromMillis(t - from.toMillis()).toISO() ?? 'PT0S',
          campaign: campaignKey,
          value: Math.round(value * 100) / 100,
        })
        t += bucketMs
        i++
      }
    }
    result[seriesRef] = points
  }

  return result
}
