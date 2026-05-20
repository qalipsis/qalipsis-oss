import { Router } from 'express'
import { db } from '../db.js'

export const scenariosRouter = Router()

scenariosRouter.get('/', (_req, res) => {
  res.json(db.scenarios)
})
