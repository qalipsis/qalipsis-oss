import { Router } from 'express'
import { db } from '../db.js'

export const zonesRouter = Router()

zonesRouter.get('/', (_req, res) => {
  res.json(db.zones)
})
