<template>
    <a-modal
        :open="open"
        :footer="null"
        :closable="closable"
        @cancel="handleCancel">
        <header class="header-section">
            <h2>{{ title }}</h2>
        </header>
        <div class="content-section">
            <slot></slot>
        </div>
        <footer class="footer-section">
            <BaseButton
                :btn-style="'stroke'"
                :text="cancelBtnText"
                @click="emit('cancelBtnClick')"
            />
            <BaseButton
                :text="confirmBtnText"
                @click="emit('confirmBtnClick')"
            />
        </footer>
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

}>();
const emit = defineEmits<{
    /**
     * Cancel button click event emitter.
     */
    (e: "cancelBtnClick"): void,
    /**
     * Confirm button click event emitter.
     */
    (e: "confirmBtnClick"): void,
    /**
     * Close button click event emitter
     */
    (e: "close"): void

}>();

const confirmBtnText = props.confirmBtnText ?? "Confirm";
const cancelBtnText = props.cancelBtnText ?? "Cancel";

const handleCancel = () => {
    // Only emits the close the event when the modal is closable
    if (props.closable) {
        emit("close");
    }
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