export const ErrorHelper = {
    getErrorMessage(error: any): string {
        const status = error?.response?.status ?? error?.status ?? error?.statusCode
        switch (status) {
            case 401:
                return error?.data?.message ?? 'Your session has expired. Please log in again.'
            case 403:
                return 'You don\'t have the permission'
            case 400:
                if (error?.data?.errors?.[0] && typeof error.data.errors[0] === 'string') {
                    return error.data.errors[0]
                }
                if (error?.data?.errors?.[0]?.message) {
                    return error.data.errors[0].message
                }

                return error?.data?.message ?? 'Bad Request'
            case 404:
                return 'The requested resource was not found'
            default:
                if (status >= 500) {
                    return 'A server error occurred. Please try again.'
                }

                return error?.data?.message ?? error?.message ?? 'Unknown Error'
        }
    },
}
