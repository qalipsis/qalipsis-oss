import { Router } from 'express'
import { db } from '../db.js'

export const configurationRouter = Router()

configurationRouter.get('/campaign', (_req, res) => {
  res.json(db.defaultCampaignConfiguration)
})
