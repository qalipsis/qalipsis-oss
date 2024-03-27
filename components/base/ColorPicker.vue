<template>
    <section v-if="open" class="color-picker">
        <header class="flex space-between">
            <label class="text-grey-1">Color picker</label>
            <div class="cursor-pointer close-btn" @click="handleCloseBtnClick">
                <img src="/icons/icon-close-black.svg" alt="">
            </div>
        </header>
        <Sketch :model-value="hexCodeValue" @update:model-value="handleColorChange($event)"/>
    </section>
</template>

<script setup lang="ts">
import { type Payload, Sketch } from '@ckpack/vue-color';

defineProps<{
    open: boolean
    /**
     * The hex code value with opacity
     * E.g., #194D33A8
     */
    hexCodeValue: string
}>()

const debouncedColorChange = debounce((color: Payload) => {
    emit("update:hexCodeValue", color.hex8);
    emit("change", color.hex8);
}, 100);

const emit = defineEmits<{
    /**
     * Two way binding event to close the color picker
     */
     (e: "update:open", value: boolean): void
    /**
     * Two way binding event to update the hex code value
     */
    (e: "update:hexCodeValue", value: string): void
    /**
     * Emits the new color
     */
    (e: "change", value: string): void
}>()

const handleCloseBtnClick = () => {
    emit("update:open", false);
}

const handleColorChange = (color: Payload) => {
    debouncedColorChange(color);
}

</script>

<style scoped lang="scss">
@import "../../assets/scss/variables";
@import "../../assets/scss/color";

.color-picker {
    padding: .25rem .5rem;
    border-radius: $default-radius;
    border: 1px solid $grey-3;
    background: #fff;
    box-shadow: 0 0 0 1px rgba(0,0,0,.15), 0 8px 16px rgba(0,0,0,.15);
}

.close-btn {
    img {
        width: 1rem;
        height: 1rem;
    }
}

:deep(.vc-sketch) {
    padding: 0;
    box-shadow: none;
    border: none;
}

:deep(.vc-sketch-presets) {
    display: none;
}
</style>