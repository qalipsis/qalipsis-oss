export class ErrorHelper {
    static handleHttpRequestError(error: any): void {
        let errorMessage = '';
        switch (error.response.status) {
            case 403:
                errorMessage = 'You don\'t have the permission';
                break;
            case 400:
                if (error.response._data.errors?.[0]) {
                    errorMessage = error.response._data.errors[0]
                } else {
                    errorMessage = error.response._data?.message ? error.response.data?.message : 'Bad Request';
                }
                break;
            default:
                errorMessage = 'Unknown Error';
                break;
        }
        NotificationHelper.error(errorMessage);
    }
}