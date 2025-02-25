<template>
    <section v-if="open" class=" px-1 py-2 rounded-md border border-solid border-gray-300 bg-white shadow-md">
        <header class="flex justify-between">
            <label class="text-gray-500">Color picker</label>
            <div class="cursor-pointer" @click="handleCloseBtnClick">
                <BaseIcon icon="qls-icon-close" class="text-base hover:text-primary-500"></BaseIcon>
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
:deep(.vc-sketch) {
    padding: 0;
    box-shadow: none;
    border: none;
}

:deep(.vc-sketch-presets) {
    display: none;
}
</style>