<template>
    <transition
        appear
        enter-active-class="transition-opacity ease-in-out duration-200"
        leave-active-class="transition-opacity ease-in-out duration-200"
        enter-from-class="opacity-0"
        leave-to-class="opacity-0"
    >
        <div
            v-if="open"
            class="fixed inset-0 z-20 bg-gray-950 bg-opacity-60 flex justify-center p-6"
            @click="maskClosable && handleCancelBtnClick"
        >
        </div>
    </transition>
    <transition
        appear
        enter-active-class="transition-all ease-in-out duration-300"
        leave-active-class="transition-all ease-in-out duration-300"
        enter-from-class="translate-x-full"
        leave-to-class="translate-x-full"
    >
        <div
            v-if="open"
            class="fixed top-0 right-0 z-20 p-6 h-full bg-white shadow-lg translate-none"
            :style="{ width: drawerWidth }"
        >
            <section class="flex flex-col h-full">
                <header class="flex justify-between mb-2">
                    <BaseTitle :content="title"  />
                    <div 
                        class="w-10 h-10 cursor-pointer flex items-center justify-center"
                        @click="handleCancelBtnClick"
                    >
                        <BaseIcon icon="qls-icon-close" class="text-2xl hover:text-primary-500"></BaseIcon>
                    </div>
                </header>
                <div class="flex-grow h-full overflow-auto">
                    <slot></slot>
                </div>
                <footer class="w-full" v-if="!footerHidden">
                    <div class="my-5">
                        <BaseDivideLine />
                    </div>
                    <div class="grid grid-cols-2 gap-x-3">
                        <BaseButton
                            class="w-full"
                            btn-style="outlined"
                            :text="cancelBtnText"
                            @click="handleCancelBtnClick"
                        />
                        <BaseButton 
                            class="w-full"
                            :text="confirmBtnText"
                            :disabled="confirmBtnDisabled"
                            @click="emit('confirmBtnClick')"
                        />
                    </div>
                </footer>
            </section>
        </div>
    </transition>
</template>

<script setup lang="ts">
const props = defineProps<{
    /**
     * A flag to indicate if the drawer should be displayed
     */
    open: boolean;

    /**
     * The title of the drawer.
     */
    title: string;

    /**
     * A flag to indicate if the drawer can be closed by mask
     */
    maskClosable?: boolean;

    /**
     * A flag to indicate if the footer should be displayed
     */
    footerHidden?: boolean;

    /**
     * The text for the confirm button.
     */
    confirmBtnText?: string;

    /**
     * A flag to indicate if the confirm button should be disabled
     */
    confirmBtnDisabled?: boolean;

    /**
     * The text for the cancel button.
     */
    cancelBtnText?: string;

    /**
     * The custom width of the drawer
     */
    width?: number;

    /**
     * The size of the drawer default | large
     */
    size?: "default" | "large" | undefined;
}>();
const emit = defineEmits<{
    /**
     * Confirm button click event emitter.
     */
    (e: "confirmBtnClick"): void,
    /**
     * Close event emitter
     */
     (e: "close"): void,
    /**
     * Cancel button click event emitter.
     */
    (e: "update:open", v: boolean): void,

}>();

const confirmBtnText = computed(() => props.confirmBtnText ?? "Confirm");
const cancelBtnText = computed(() => props.cancelBtnText ?? "Cancel");

const drawerWidth = computed(() => props.width ? `${props.width}px`: '660px');

const handleCancelBtnClick = () => {
    emit("update:open", false);
    emit("close")
}

</script>
