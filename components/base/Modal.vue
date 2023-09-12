<template>
    <a-modal
        :open="open"
        :footer="null"
        :closable="closable"
        @cancel="handleCancelBtnClick">
        <header class="header-section">
            <BaseTitle :content="title" />
        </header>
        <div class="content-section">
            <slot></slot>
        </div>
        <div v-if="$slots.customFooter">
            <slot name="customFooter"></slot>
        </div>
        <div v-else>
            <footer v-if="!footerHidden" class="footer-section">
                <BaseButton
                    :btn-style="'stroke'"
                    :text="cancelBtnText"
                    @click="handleCancelBtnClick"
                />
                <BaseButton
                    :text="confirmBtnText"
                    @click="emit('confirmBtnClick')"
                />
            </footer>
        </div>

    </a-modal>
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

const confirmBtnText = props.confirmBtnText ?? "Confirm";
const cancelBtnText = props.cancelBtnText ?? "Cancel";

const handleCancelBtnClick = () => {
    emit("update:open", false);
    emit("close")
}

</script>

<style scoped lang="scss">

@mixin default-section {
    display: flex;
    align-items: center;
}

.header-section {
    @include default-section;
    padding: 1.25rem 0;
    justify-content: center;
}

.content-section {
    @include default-section;
    justify-content: center;
    padding: 1rem 1rem 2rem 1rem;
}

.footer-section {
    @include default-section;
    justify-content: space-around;
}

</style>