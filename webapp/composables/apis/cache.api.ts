import {add, isBefore} from 'date-fns'
import type {FetchOptions} from 'ofetch'

interface CacheItem {
    /**
     * The cached response
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

export const cacheApi = () => {
    const {get$, api$} = baseApi()

    const apiCache$ = async <T>(url: string, options: FetchOptions, ttl = DEFAULT_TTL): Promise<T> => {
        const cacheKey = getCacheKey(url, options)

        if (isValidCache(cacheKey)) return Promise.resolve(_cache[cacheKey].response)

        const response = await api$<T>(url, options)
        storeCache(cacheKey, response, ttl)

        return response
    }

    const getCache$ = async <T, R>(url: string, query: { [key: string]: R }, ttl = DEFAULT_TTL): Promise<T> => {
        const cacheKey = getCacheKey(url, query)

        if (isValidCache(cacheKey)) return Promise.resolve(_cache[cacheKey].response)

        const response = await get$<T, R>(url, query)
        storeCache(cacheKey, response, ttl)

        return response
    }

    /**
     * Clears the cache
     *
     * @param url The request url
     * @param param The request param
     */
    const clearCache = (url: string, param?: any) => {
        const cacheKey = getCacheKey(url, param)
        delete _cache[cacheKey]
    }

    /**
     * Stores the response to the cache
     *
     * @param cacheKey the key of the cache request
     * @param response the response from the request url
     * @param ttl time to live
     */
    const storeCache = (cacheKey: string, response: any, ttl: number) => {
        _cache[cacheKey] = {
            expirationTime: add(new Date(), {seconds: ttl}),
            response: response,
        }
    }

    /**
     * Gets the key for the cache
     *
     * @param url The request url
     * @param param The request param
     * @returns The key for storing the cache
     */
    const getCacheKey = (url: string, param?: any): string => {
        const tenant = localStorage.getItem(TenantHelper.TENANT_LOCAL_STORAGE_PROPERTY_KEY)

        return `${url}/${tenant}/${param ? JSON.stringify(param) : ''}`
    }

    /**
     * Checks if the cache is still valid
     *
     * @param cacheKey key of the cache
     * @returns a flag to indicate if the cache is still valid
     */
    const isValidCache = (cacheKey: string): boolean => {
        // When the cache is not null, returns the result to check if the token is expired.
        if (_cache[cacheKey]) {
            return isBefore(new Date(), _cache[cacheKey].expirationTime)
        }

        // No cache found.
        return false
    }

    return {
        apiCache$,
        getCache$,
        clearCache,
    }
}
