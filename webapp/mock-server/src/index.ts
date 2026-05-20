import express from 'express'
import cors from 'cors'
import morgan from 'morgan'
import { seed } from './seed.js'
import { campaignsRouter } from './routes/campaigns.js'
import { dataSeriesRouter } from './routes/dataSeries.js'
import { reportsRouter } from './routes/reports.js'
import { scenariosRouter } from './routes/scenarios.js'
import { timeSeriesRouter } from './routes/timeSeries.js'
import { usersRouter } from './routes/users.js'
import { zonesRouter } from './routes/zones.js'
import { configurationRouter } from './routes/configuration.js'

const PORT = Number(process.env.PORT ?? 8401)
const LATENCY_MS = Number(process.env.MOCK_LATENCY_MS ?? 0)

seed()

const app = express()

app.use(cors())
app.use(morgan('dev'))
app.use(express.json({ limit: '5mb' }))

if (LATENCY_MS > 0) {
  app.use((_req, _res, next) => setTimeout(next, LATENCY_MS))
}

app.get('/health', (_req, res) => res.json({ status: 'ok' }))

app.use('/campaigns', campaignsRouter)
app.use('/data-series', dataSeriesRouter)
app.use('/reports', reportsRouter)
app.use('/scenarios', scenariosRouter)
app.use('/time-series', timeSeriesRouter)
app.use('/users', usersRouter)
app.use('/zones', zonesRouter)
app.use('/configuration', configurationRouter)

app.use((req, res) => {
  console.warn(`[mock] unhandled ${req.method} ${req.originalUrl}`)
  res.status(404).json({ message: `No mock handler for ${req.method} ${req.originalUrl}` })
})

app.listen(PORT, () => {
  console.log(`[mock] qalipsis mock-server listening on http://localhost:${PORT}`)
  if (LATENCY_MS > 0) console.log(`[mock] injecting ${LATENCY_MS}ms latency per request`)
})
