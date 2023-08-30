export const baseApi = () => {
    const config = useRuntimeConfig();

    const get$ = <T, R>(url: string, query: { [key: string]: R }): Promise<T> => {
        return $fetch(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'get',
            query: {
                ...query
            }, 
            headers: getRequestHeaders()
        })
    }

    const post$ = <T, R>(url: string, requestParams: R): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'post',
            body: JSON.stringify(requestParams),
            headers: getRequestHeaders()
        })
    }

    const delete$ = <T>(url: string): Promise<T> => {
        return $fetch<T>(url, {
            baseURL: config.public.apiBaseUrl,
            method: 'delete',
            headers: getRequestHeaders()
        })
    } 

    const getRequestHeaders = (): HeadersInit => {
        const requestHeader: { [key: string]: string } = {};
        const currentTenant = localStorage.getItem(TenantHelper.TENANT_LOCAL_STORAGE_PROPERTY_KEY);

        if (currentTenant) {
            requestHeader[TenantHelper.TENANT_REQUEST_HEADER_KEY] = currentTenant
        }

        return requestHeader;
    }

    return {
        get$,
        post$,
        delete$
    }
}