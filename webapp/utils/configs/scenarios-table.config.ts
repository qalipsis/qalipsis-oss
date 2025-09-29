import type { TableColumnConfig } from '../types/table'

export const SCENARIO_TABLE_COLUMNS: TableColumnConfig[] = [
  {
    title: 'Scenario',
    key: 'name',
    sortingEnabled: true,
  },
  {
    title: 'Version',
    key: 'version',
    sortingEnabled: true,
  },
  {
    title: 'Description',
    key: 'description',
    sortingEnabled: true,
  },
]
