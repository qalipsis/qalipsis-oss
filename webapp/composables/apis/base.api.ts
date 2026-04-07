import type { FetchOptions } from 'ofetch'

export const baseApi = () => {
  const config = useRuntimeConfig()
  const baseURL = config.public.apiBaseUrl

  /**
   * Generic fetch wrapper that accepts raw FetchOptions.
   * Use this for advanced cases such as blob downloads (responseType: 'blob') or custom retry logic.
   */
  const api$ = <T>(url: string, options: FetchOptions): Promise<T> => {
    return $fetch(url, {
      baseURL,
      ...options,
    }) as Promise<T>
  }

  const get$ = <T, R>(url: string, query?: { [key: string]: R }): Promise<T> => {
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
      body: requestParams ?? undefined,
    }) as Promise<T>
  }

  const put$ = <T, R>(url: string, requestParams: R): Promise<T> => {
    return $fetch(url, {
      baseURL,
      method: 'PUT',
      body: requestParams,
    }) as Promise<T>
  }

  const patch$ = <T, R>(url: string, requestParams: R): Promise<T> => {
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
    get$,
    post$,
    patch$,
    delete$,
    put$,
  }
}
