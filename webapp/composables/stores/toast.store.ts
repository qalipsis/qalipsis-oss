interface ToastStore {
    toasts: Toast[];
}

const createToast = (text: string, status: ToastStatus): Toast => ({
    text,
    status,
    id: Math.random() * 1000,
});

export const useToastStore = defineStore("toastStore", {
    state: (): ToastStore => {
        return {
            toasts: [],
        };
    },
    actions: {
        updateState(payload: ToastPayload, status: ToastStatus) {
            const {text, timeout} = payload;
            const toast = createToast(text, status);
            this.toasts.push(toast);
            setTimeout(() => {
                this.toasts = this.toasts.filter((t) => t.id !== toast.id);
            }, timeout ?? ToastConfig.defaultTimeout);
        },

        success(payload: ToastPayload) {
            this.updateState(payload, 'success');
        },

        warning(payload: ToastPayload) {
            this.updateState(payload, 'warning');
        },

        error(payload: ToastPayload) {
            this.updateState(payload, 'error');
        },

        info(payload: ToastPayload) {
            this.updateState(payload, 'info');
        },
    },
});
