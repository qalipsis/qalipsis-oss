import { Router } from 'express'
import { db } from '../db.js'

export const usersRouter = Router()

usersRouter.get('/profile', (_req, res) => {
  res.json(db.profile)
})

usersRouter.get('/permissions', (_req, res) => {
  res.json(db.permissions)
})
