import type { Page, PageQueryParams } from '@webapp-types/page'

export interface PaginateOptions<T> {
  filterFields?: (keyof T)[]
}

const DEFAULT_PAGE_SIZE = 20

const stripWildcards = (s: string) => s.replace(/^\*+|\*+$/g, '')

const matchesFilter = <T>(item: T, filter: string, fields: (keyof T)[]): boolean => {
  const needle = stripWildcards(filter).toLowerCase()
  if (!needle) return true

  return fields.some((field) => {
    const value = item[field]
    return value != null && String(value).toLowerCase().includes(needle)
  })
}

const compareBy = <T>(field: keyof T, direction: 'asc' | 'desc') => (a: T, b: T): number => {
  const av = a[field]
  const bv = b[field]
  if (av == null && bv == null) return 0
  if (av == null) return direction === 'asc' ? -1 : 1
  if (bv == null) return direction === 'asc' ? 1 : -1
  const cmp = String(av).localeCompare(String(bv))

  return direction === 'asc' ? cmp : -cmp
}

export const paginate = <T>(
  items: T[],
  query: PageQueryParams,
  options: PaginateOptions<T> = {},
): Page<T> => {
  const filterFields = options.filterFields ?? []
  let filtered = items

  if (query.filter && filterFields.length > 0) {
    filtered = items.filter((item) => matchesFilter(item, query.filter!, filterFields))
  }

  if (query.sort) {
    const [field, dirRaw] = query.sort.split(':')
    const direction = (dirRaw ?? 'asc').toLowerCase() === 'desc' ? 'desc' : 'asc'
    if (field) {
      filtered = [...filtered].sort(compareBy(field as keyof T, direction))
    }
  }

  const size = Math.max(1, query.size ?? DEFAULT_PAGE_SIZE)
  const page = Math.max(0, query.page ?? 0)
  const totalElements = filtered.length
  const totalPages = totalElements === 0 ? 0 : Math.ceil(totalElements / size)
  const start = page * size
  const elements = filtered.slice(start, start + size)

  return { page, totalPages, totalElements, elements }
}
