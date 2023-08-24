
const placement = "bottomRight";
const styleClass = "notification";

export class NotificationHelper {
    
    static success(message: string): void {
        notification.success({
            placement: placement,
            class: styleClass,
            message: message,
            icon: () => h('img', { src: '/icons/icon-notification-rocket.svg', width: 60 }),
            closeIcon: () => h('img', { src: '/icons/icon-close-black.svg', width: 18 })
        })
    }

    static info(message: string): void {
        notification.info({
            placement: placement,
            class: styleClass,
            message: message,
            icon: () => h('img', { src: '/icons/icon-info-emerald.svg', width: 60 }),
            closeIcon: () => h('img', { src: '/icons/icon-close-black.svg', width: 18 })
        })
    }

    
    static warning(message: string): void {
        notification.warning({
            placement: placement,
            class: styleClass,
            message: message,
            icon: () => h('img', { src: '/icons/icon-info-yellow.svg', width: 60 }),
            closeIcon: () => h('img', { src: '/icons/icon-close-black.svg', width: 18 })
        })
    }

    static error(message: string): void {
        notification.error({
            placement: placement,
            class: `${styleClass} ${styleClass}--error`,
            duration: 0,
            message: message,
            icon: () => h('img', { src: '/icons/icon-notification-error.svg', width: 100 }),
            closeIcon: () => h('img', { src: '/icons/icon-close-black.svg', width: 18 })
        })
    }

}