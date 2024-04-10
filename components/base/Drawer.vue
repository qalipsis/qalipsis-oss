<template>
    <a-drawer 
        placement="right"
        :open="open"
        :size="drawerSize"
        :closable="false"
        :mask-closable="maskClosable"
        :width="width"
        @close="handleCancelBtnClick"
        :destroy-on-close="true">
        <section class="flex flex-col h-full">
            <header class="flex justify-between mb-2">
                <BaseTitle :content="title"  />
                <div 
                    class="w-10 h-10 cursor-pointer flex items-center justify-center"
                    :class="TailwindClassHelper.primaryColorFilterHoverClass"
                    @click="handleCancelBtnClick"
                >
                    <img src="/icons/icon-close-black.svg" alt="">
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
    </a-drawer>
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
const drawerSize = props.size ?? "large";

const handleCancelBtnClick = () => {
    emit("update:open", false);
    emit("close")
}

</script>
