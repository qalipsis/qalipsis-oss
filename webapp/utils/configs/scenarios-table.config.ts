import type { TableColumnConfig } from '../types/table'

export const SCENARIO_TABLE_COLUMNS: TableColumnConfig[] = [
  {
    title: 'Scenario',
    key: 'name',
    sortingEnabled: true,
    width: '25%',
  },
  {
    title: 'Version',
    key: 'version',
    sortingEnabled: true,
    width: '10%',
  },
  {
    title: 'Description',
    key: 'description',
    sortingEnabled: true,
  },
]
