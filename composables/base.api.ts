export const baseApi = () => {
    const config = useRuntimeConfig();

    const get$ = <T, R>(url: string, query: { [key: string]: R }): Promise<T> => {
        return $fetch(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'get',
            query: {
                ...query
            }, 
        })
    }

    const post$ = <T, R>(url: string, requestParams: R): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'post',
            body: JSON.stringify(requestParams),
        })
    }

    const delete$ = <T>(url: string): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'delete',
        })
    } 

    return {
        get$,
        post$,
        delete$
    }
}