export class ErrorHelper {
    static handleHttpResponseError(error: any): void {
        let errorMessage = '';
        // console.log(error)
        console.log(error.data)
        console.log(error.response)
        switch (error.response.status) {
            case 403:
                errorMessage = 'You don\'t have the permission';
                break;
            case 400:
                if (error.data.errors?.[0]) {
                    errorMessage = error.data.errors[0]
                } else if (error.data.errors?.[0]?.message) {
                    errorMessage = error.data.errors[0].message
                } else {
                    errorMessage = error.data?.message ?? 'Bad Request';
                }
                break;
            default:
                errorMessage = 'Unknown Error';
                break;
        }
        NotificationHelper.error(errorMessage);
    }
}