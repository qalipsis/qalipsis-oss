export const ErrorHelper = {
    getErrorMessage(error: any): string {
        let errorMessage = ''
        const status = error?.response?.status ?? error?.status ?? error?.statusCode
        switch (status) {
            case 401:
                errorMessage = error?.data?.message ?? 'Your session has expired. Please log in again.'
                break
            case 403:
                errorMessage = 'You don\'t have the permission'
                break
            case 400:
                if (error?.data?.errors?.[0] && typeof error.data.errors[0] === 'string') {
                    errorMessage = error.data.errors[0]
                } else if (error?.data?.errors?.[0]?.message) {
                    errorMessage = error.data.errors[0].message
                } else {
                    errorMessage = error?.data?.message ?? 'Bad Request'
                }
                break
            case 404:
                errorMessage = 'The requested resource was not found'
                break
            default:
                if (status >= 500) {
                    errorMessage = 'A server error occurred. Please try again.'
                } else {
                    errorMessage = error?.data?.message ?? error?.message ?? 'Unknown Error'
                }
                break
        }

        return errorMessage
    },
}
