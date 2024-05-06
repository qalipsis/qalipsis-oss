export class ToastConfig {
    static readonly defaultTimeout = 3000;
    static readonly toastStatusToIcon: { [key in ToastStatus]: string } = {
        success: "/icons/icon-notification-rocket.svg",
        info: "/icons/icon-info-emerald.svg",
        warning: "/icons/icon-info-yellow.svg",
        error: "/icons/icon-notification-error.svg"
    }
    static readonly toastStatusToTailwindClass: { [key in ToastStatus]: string } = {
        success: "bg-purple-50 text-purple-950",
        info: "bg-primary-50 text-primary-950",
        warning: "bg-yellow-50 text-yellow-950",
        error: "bg-red-50 text-red-950"
    }
}