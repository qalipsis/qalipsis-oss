import { add, isBefore } from 'date-fns'
import type { FetchOptions } from 'ofetch'

interface CacheItem {
  /**
   * The cached response. Typed as `any` because the cache is generic and can hold arbitrary API response shapes.
   */
  response: any

  /**
   * The expire time of the cache
   */
  expirationTime: Date
}

/**
 * The default time to live value in seconds for the cache.
 */
const DEFAULT_TTL = 180

const _cache: { [key: string]: CacheItem } = {}

// `param` is typed as `any` to accept arbitrary query objects or fetch options without constraining the caller.
const getCacheKey = (url: string, ttl: number, param?: any): string => {
  const tenant = localStorage.getItem(TenantConfig.TENANT_LOCAL_STORAGE_PROPERTY_KEY)

  return `${url}/${tenant}/${ttl}/${param ? JSON.stringify(param, param && typeof param === 'object' ? Object.keys(param).sort() : undefined) : ''}`
}

const getValidCacheItem = (cacheKey: string): CacheItem | undefined => {
  const item = _cache[cacheKey]

  return item && isBefore(new Date(), item.expirationTime) ? item : undefined
}

// `response` is typed as `any` to match the generic `CacheItem.response` field.
const storeCache = (cacheKey: string, response: any, ttl: number) => {
  _cache[cacheKey] = {
    expirationTime: add(new Date(), { seconds: ttl }),
    response: response,
  }
}

export const useCacheApi = () => {
  const { get$, api$ } = baseApi()

  const withCache = async <T>(cacheKey: string, ttl: number, fetcher: () => Promise<T>): Promise<T> => {
    const cached = getValidCacheItem(cacheKey)
    if (cached) return Promise.resolve(cached.response)

    const response = await fetcher()
    storeCache(cacheKey, response, ttl)

    return response
  }

  const apiCache$ = async <T>(url: string, options: FetchOptions, ttl = DEFAULT_TTL): Promise<T> => {
    const cacheKey = getCacheKey(url, ttl, options)

    return withCache(cacheKey, ttl, () => api$<T>(url, options))
  }

  const getCache$ = async <T, R extends object = Record<string, unknown>>(url: string, query?: R, ttl = DEFAULT_TTL): Promise<T> => {
    const cacheKey = getCacheKey(url, ttl, query)

    return withCache(cacheKey, ttl, () => get$<T, R>(url, query))
  }

  /**
   * Clears the cache
   *
   * @param url The request url
   * @param ttl The time to live used when the cache was stored
   * @param param The request param
   */
  // `param` is typed as `any` to mirror the signature of `getCacheKey`, which accepts arbitrary query params.
  const clearCache = (url: string, ttl = DEFAULT_TTL, param?: any) => {
    const cacheKey = getCacheKey(url, ttl, param)
    delete _cache[cacheKey]
  }

  return {
    apiCache$,
    getCache$,
    clearCache,
  }
}
