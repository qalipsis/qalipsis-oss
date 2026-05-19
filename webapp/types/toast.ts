export type ToastStatus = 'success' | 'error' | 'warning' | 'info';

export interface Toast {
    /**
     * Text of the toast
     */
    text: string;

    /**
     * The status of the toast.
     */
    status: ToastStatus;

    /**
     * Id to differentiate toasts
     */
    id: number;
}

export interface ToastPayload {
    text: string;
    timeout?: number;
}