export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'get' | 'post' | 'put' | 'patch' | 'delete'
  body?: unknown
  query?: Record<string, unknown>
  responseType?: 'json' | 'text' | 'blob' | 'arrayBuffer' | 'stream'
  retry?: number
  retryDelay?: number
    retryStatusCodes?: number[]
}

export const baseApi = () => {
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBaseUrl

  /**
   * Generic fetch wrapper that accepts raw FetchOptions.
   * Use this for advanced cases such as blob downloads (responseType: 'blob') or custom retry logic.
   */
  const api$ = <T>(url: string, options: ApiRequestOptions): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: options.method,
      body: options.body as Record<string, unknown> | undefined,
      query: options.query,
      responseType: options.responseType,
      retry: options.retry,
      retryDelay: options.retryDelay,
        retryStatusCodes: options.retryStatusCodes,
    }) as Promise<T>
  }

    /**
     * Same as api$ but returns the full FetchResponse so callers can read headers
     * (e.g. Content-Disposition for file downloads).
     */
    const apiRaw$ = <T>(url: string, options: ApiRequestOptions) => {
        return $fetch.raw<T>(url, {
            baseURL,
            method: options.method,
            body: options.body as Record<string, unknown> | undefined,
            query: options.query,
            responseType: options.responseType,
            retry: options.retry,
            retryDelay: options.retryDelay,
            retryStatusCodes: options.retryStatusCodes,
        })
    }

  const get$ = <T, R extends object = Record<string, unknown>>(url: string, query?: R): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: 'GET',
      query,
    }) as Promise<T>
  }

  const post$ = <T>(url: string, requestParams?: unknown): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: 'POST',
      body: requestParams as Record<string, unknown>,
    }) as Promise<T>
  }

  const put$ = <T, R extends object>(url: string, requestParams: R): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: 'PUT',
      body: requestParams,
    }) as Promise<T>
  }

  const patch$ = <T, R extends object>(url: string, requestParams: R): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: 'PATCH',
      body: requestParams,
    }) as Promise<T>
  }

  const delete$ = <T>(url: string): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: 'DELETE',
    }) as Promise<T>
  }

  return {
    api$,
      apiRaw$,
    get$,
    post$,
    patch$,
    delete$,
    put$,
  }
}
