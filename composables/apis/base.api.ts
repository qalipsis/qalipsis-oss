export const baseApi = () => {
    const config = useRuntimeConfig();

    const get$ = <T, R>(url: string, query?: { [key: string]: R }): Promise<T> => {
        return $fetch(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'GET',
            query: {
                ...query
            }, 
        })
    }

    const post$ = <T, R>(url: string, requestParams: R): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'POST',
            body: JSON.stringify(requestParams),
        })
    }

    const put$ = <T, R>(url: string, requestParams: R): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'PUT',
            body: JSON.stringify(requestParams),
        })
    }

    const patch$ = <T, R>(url: string, requestParams: R): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'PATCH',
            body: JSON.stringify(requestParams),
        })
    }

    const delete$ = <T>(url: string): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'DELETE',
        })
    } 

    return {
        get$,
        post$,
        patch$,
        delete$,
        put$
    }
}