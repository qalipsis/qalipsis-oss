<template>
    <div>
        <transition
            enter-active-class="transition-opacity ease-in-out duration-200"
            leave-active-class="transition-opacity ease-in-out duration-200"
            enter-from-class="opacity-0"
            leave-to-class="opacity-0"
        >
            <div
                v-if="open"
                class="fixed inset-0 z-10 bg-gray-950 bg-opacity-60 flex justify-center p-6"
                @click="maskClosable && closeModal"
            >
            </div>
        </transition>
        <transition
            enter-active-class="transition-opacity ease-in-out duration-300"
            leave-active-class="transition-opacity ease-in-out duration-300"
            enter-from-class="opacity-0"
            leave-to-class="opacity-0"
        >
            <div
                v-if="open" 
                class="fixed top-20 left-0 right-0 bottom-0 z-10 w-fit max-w-[600px] transform-none bg-white rounded-md shadow-lg px-6 py-5 h-fit mx-auto">
                <header class="relative w-full flex items-center justify-center p-5">
                    <BaseTitle :content="title" />
                    <div 
                        v-if="closable"
                        class="absolute right-0 cursor-pointer"
                        :class="[
                            TailwindClassHelper.grayColorFilterClass,
                            TailwindClassHelper.primaryColorFilterHoverClass
                        ]"
                        @click="closeModal"
                    >
                        <BaseIcon icon="/icons/icon-close-black.svg" />
                    </div>
                </header>
                <div class="flex center p-4">
                    <slot></slot>
                </div>
                <slot name="customFooter">
                    <footer v-if="!footerHidden" class="flex items-center justify-around mt-8">
                        <BaseButton
                            btn-style="outlined"
                            :text="cancelBtnText"
                            @click="closeModal"
                        />
                        <BaseButton
                            :text="confirmBtnText"
                            @click="emit('confirmBtnClick')"
                        />
                    </footer>
                </slot>
            </div>
        </transition>
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
    /**
     * A flag to indicate if the modal should be displayed.
     */
    open: boolean;

    /**
     * The title of the modal.
     */
    title: string;

    /**
     * The text for the confirm button
     */
    confirmBtnText?: string;

    /**
     * The text for the confirm button
     */
    cancelBtnText?: string;

    /**
     * A flag to indicate if the close button should be displayed
     */
    closable?: boolean;

    /**
     * A flag to indicate if the modal can be closed by the mask
     */
    maskClosable?: boolean;

    /**
     * A flag to indicate if the footer should be hidden
     */
    footerHidden?: boolean;
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
     * Two way binding event
     */
    (e: "update:open", v:boolean): void

}>();

const confirmBtnText = computed(() => props.confirmBtnText ?? "Confirm");
const cancelBtnText = computed(() => props.cancelBtnText ?? "Cancel");

const closeModal = () => {
    emit("update:open", false);
    emit("close")
}

</script>
