<template>
    <a-drawer 
        placement="right"
        :open="open"
        :size="drawerSize"
        :closable="false"
        :mask-closable="maskClosable"
        :width="width"
        :destroy-on-close="true">
        <section class="base-drawer-section">
            <header class="drawer-header-section">
                <BaseTitle :content="title"  />
                <div class="close-btn" @click="emit('cancelBtnClick')">
                    <img src="/icons/icon-close-black.svg" alt="">
                </div>
            </header>
            <div class="drawer-content-section">
                <slot></slot>
            </div>
            <footer class="drawer-footer-section" v-if="!footerHidden">
                <hr class="divide-line">
                <div class="button-groups">
                    <div class="button-wrapper">
                        <BaseButton
                            class="full-width"
                            :btn-style="'stroke'"
                            :text="cancelBtnText"
                            @click="emit('cancelBtnClick')"
                        />
                    </div>
                    <div class="button-wrapper">
                        <BaseButton 
                            class="full-width"
                            :text="confirmBtnText"
                            @click="emit('confirmBtnClick')"
                        />
                    </div>
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
     * Cancel button click event emitter.
     */
    (e: "cancelBtnClick"): void
}>();

const confirmBtnText = props.confirmBtnText ?? "Confirm";
const cancelBtnText = props.cancelBtnText ?? "Cancel";
const drawerSize = props.size ?? "large";

</script>

<style scoped lang="scss">
@import "../../assets/scss/color";

.base-drawer-section {
    display: flex;
    flex-direction: column;
    height: 100%;

    .drawer-header-section {
        display: flex;
        justify-content: space-between;

        .close-btn {
            width: 2.5rem;
            height: 2.5rem;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;

            &:hover {
                img {
                    filter: $primary-color-svg;
                }
            }
        }
    }

    .drawer-content-section {
        flex-grow: 1;
        height: 100%;
        overflow: auto;
    }

    .drawer-footer-section {
        width: 100%;

        .divide-line {
            margin: 1.25rem 0;
        }

        .button-groups {
            display: flex;
            align-items: center;
            justify-content: space-between;

            .button-wrapper {
                width: 45%;
            }
        }
    }
}

</style>