export const useApi = () => {
    const get = <T>(url: string, query?: { [key: string]: any }): Promise<T> => {
        console.log('get from base')

        return $fetch<T>(url, {
            method: 'GET',
            query: {
                ...query
            }
        })
    }

    const post = <T, R>(url: string, requestParams: R): Promise<T> => {
        return $fetch<T>(url, {
            method: 'POST',
            body: JSON.stringify(requestParams)
        })
    }


    return {
        get,
        post
    }
}