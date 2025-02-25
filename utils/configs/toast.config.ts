export class ToastConfig {
    static readonly defaultTimeout = 3000;
    static readonly toastStatusToIcon: { [key in ToastStatus]: string } = {
        success: "qls-icon-rocket",
        info: "qls-icon-info",
        warning: "qls-icon-info",
        error: "qls-icon-error-chat"
    }
    static readonly toastStatusIconToTailwindClass: { [key in ToastStatus]: string } = {
        success: "text-purple-600 text-5xl",
        info: "text-primary-600 text-5xl",
        warning: "text-yellow-600 text-5xl",
        error: "text-red-600 text-7xl"
    }
    static readonly toastStatusToTailwindClass: { [key in ToastStatus]: string } = {
        success: "bg-purple-50 text-purple-950",
        info: "bg-primary-50 text-primary-950",
        warning: "bg-yellow-50 text-yellow-950",
        error: "bg-red-50 text-red-950"
    }
}