export class ErrorHelper {
    static getErrorMessage(error: any): string {
        let errorMessage = '';
        switch (error?.response?.status) {
            case 401:
                errorMessage = error.data.message;
                break;
            case 403:
                errorMessage = 'You don\'t have the permission';
                break;
            case 400:
                if (error.data?.errors?.[0] && typeof error.data.errors[0] === 'string') {
                    errorMessage = error.data.errors[0]
                } else if (error.data?.errors?.[0]?.message) {
                    errorMessage = error.data.errors[0].message
                } else {
                    errorMessage = error.data?.message ?? 'Bad Request';
                }
                break;
            default:
                errorMessage = 'Unknown Error';
                break;
        }

        return errorMessage;
    }
}