export const ToastConfig = {
    defaultTimeout: 3000,
    toastStatusToIcon: {
        success: "qls-icon-rocket",
        info: "qls-icon-info",
        warning: "qls-icon-info",
        error: "qls-icon-error-chat"
    } as { [key in ToastStatus]: string },
    toastStatusIconToTailwindClass: {
        success: "text-purple-600 dark:text-purple-200 text-5xl",
        info: "text-primary-600 dark:text-primary-200 text-5xl",
        warning: "text-yellow-600 dark:text-yellow-200 text-5xl",
        error: "text-red-600 dark:text-red-200 text-7xl"
    } as { [key in ToastStatus]: string },
    toastStatusToTailwindClass: {
        success: "bg-purple-50 text-purple-950 dark:bg-purple-800 dark:text-purple-50",
        info: "bg-primary-50 text-primary-900 dark:bg-primary-800 dark:text-primary-50",
        warning: "bg-yellow-50 text-yellow-950 dark:bg-yellow-800 dark:text-yellow-50",
        error: "bg-red-50 text-red-950 dark:bg-red-800 dark:text-red-50"
    } as { [key in ToastStatus]: string },
}
