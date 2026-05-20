import { Router } from 'express'
import type {
  DataSeries,
  DataSeriesCreationRequest,
  DataSeriesPatch,
  DataField,
} from '@webapp-types/series'
import type { PageQueryParams } from '@webapp-types/page'
import { db } from '../db.js'
import { paginate } from '../paginate.js'

export const dataSeriesRouter = Router()

const numberOrUndefined = (v: unknown): number | undefined => {
  if (v == null) return undefined
  const n = Number(v)

  return Number.isFinite(n) ? n : undefined
}

const toPageQuery = (q: Record<string, unknown>): PageQueryParams & { campaign?: string } => ({
  filter: typeof q.filter === 'string' ? q.filter : undefined,
  sort: typeof q.sort === 'string' ? q.sort : undefined,
  page: numberOrUndefined(q.page),
  size: numberOrUndefined(q.size),
  campaign: typeof q.campaign === 'string' ? q.campaign : undefined,
})

// Apply a single DataSeriesPatch to a DataSeries in-place. Patches are discriminated by `type`.
const applyPatch = (series: DataSeries, patch: DataSeriesPatch): void => {
  const p = patch as DataSeriesPatch & Record<string, unknown>
  switch (patch.type) {
    case 'display-name':
      if (typeof p.displayName === 'string') series.displayName = p.displayName
      break
    case 'sharing-mode':
      if (typeof p.sharingMode === 'string') series.sharingMode = p.sharingMode as DataSeries['sharingMode']
      break
    case 'color':
      if (typeof p.color === 'string') series.color = p.color
      if (typeof p.opacity === 'number') series.colorOpacity = p.opacity
      break
    case 'filters':
      if (Array.isArray(p.filters)) series.filters = p.filters as DataSeries['filters']
      break
    case 'field-name':
      if (typeof p.fieldName === 'string') series.fieldName = p.fieldName
      break
    case 'value-name':
      if (typeof p.valueName === 'string') series.valueName = p.valueName
      break
    case 'aggregation':
      if (typeof p.operation === 'string') series.aggregationOperation = p.operation as DataSeries['aggregationOperation']
      break
    case 'timeframe':
      if (typeof p.timeframe === 'string') series.timeframeUnit = p.timeframe
      break
  }
}

// GET /data-series — pageable, supports campaign filter (mock: returns all regardless of campaign value)
dataSeriesRouter.get('/', (req, res) => {
  const page = paginate(Array.from(db.dataSeries.values()), toPageQuery(req.query), {
    filterFields: ['displayName', 'valueName', 'reference'],
  })
  res.json(page)
})

// POST /data-series
dataSeriesRouter.post('/', (req, res) => {
  const body = req.body as DataSeriesCreationRequest
  const reference = `ds-${Date.now()}`
  const nowIso = new Date().toISOString()
  const created: DataSeries = {
    reference,
    version: nowIso,
    creator: 'qalipsis-dev',
    displayName: body.displayName,
    valueName: body.valueName,
    dataType: body.dataType,
    sharingMode: body.sharingMode,
    color: body.color,
    colorOpacity: body.colorOpacity,
    filters: body.filters ?? [],
    fieldName: body.fieldName,
    aggregationOperation: body.aggregationOperation,
    timeframeUnit: body.timeframeUnit,
    displayFormat: body.displayFormat,
  }
  db.dataSeries.set(reference, created)
  res.json(created)
})

// DELETE /data-series?series=ref1,ref2
dataSeriesRouter.delete('/', (req, res) => {
  const raw = req.query.series
  const refs = typeof raw === 'string' ? raw.split(',').map((r) => r.trim()).filter(Boolean) : []
  for (const r of refs) db.dataSeries.delete(r)
  res.status(204).end()
})

// PATCH /data-series/{reference} — Body is DataSeriesPatch[]
dataSeriesRouter.patch('/:reference', (req, res) => {
  const series = db.dataSeries.get(req.params.reference)
  if (!series) {
    res.status(404).json({ message: `Data series ${req.params.reference} not found` })

    return
  }
  const patches = Array.isArray(req.body) ? (req.body as DataSeriesPatch[]) : []
  for (const p of patches) applyPatch(series, p)
  series.version = new Date().toISOString()
  res.json(series)
})

// GET /data-series/{data-type}/names — auto-completion list
dataSeriesRouter.get('/:dataType/names', (req, res) => {
  const dataType = req.params.dataType.toUpperCase()
  const filterRaw = typeof req.query.filter === 'string' ? req.query.filter : ''
  const needle = filterRaw.replace(/^\*+|\*+$/g, '').toLowerCase()
  const names = Array.from(
    new Set(
      Array.from(db.dataSeries.values())
        .filter((s) => s.dataType === dataType)
        .map((s) => s.valueName)
        .filter((n) => !needle || n.toLowerCase().includes(needle)),
    ),
  )
  const size = Number(req.query.size) || 20
  res.json(names.slice(0, size))
})

// GET /data-series/{data-type}/fields
dataSeriesRouter.get('/:dataType/fields', (req, res) => {
  const fields: DataField[] = [
    { name: 'duration', type: 'NUMBER', unit: 'ms' },
    { name: 'count', type: 'NUMBER' },
    { name: 'status', type: 'STRING' },
    { name: 'timestamp', type: 'DATE' },
  ]
  res.json(fields)
})

// GET /data-series/{data-type}/tags
dataSeriesRouter.get('/:dataType/tags', (req, res) => {
  res.json({
    scenario: ['checkout-flow', 'search-latency', 'auth-burst'],
    zone: ['eu-west', 'us-east'],
    status: ['200', '4xx', '5xx'],
  })
})
