<template>
    <div ref="tooltipRef">
        <slot></slot>
    </div>
    <div v-if="$slots.tooltipContent" ref="contentRef" class="hidden">
        <slot name="tooltipContent"></slot>
    </div>
</template>

<script setup lang="ts">
import tippy from 'tippy.js';
import type { Instance } from 'tippy.js';
import 'tippy.js/dist/tippy.css';

const props = defineProps<{
    text?: string;
    position?: string;
}>()

const tooltipRef = ref<HTMLDivElement>();
const contentRef = ref<HTMLDivElement>();
const tooltipInstance = ref<Instance>();
const slots = useSlots()

const _destroyTooltip = () => {
    if (tooltipInstance.value) {
        tooltipInstance.value.destroy();
    }
}

const _initTooltip = () => {
    _destroyTooltip();
    let content = props.text;
    let allowHTML = false;
    if (contentRef.value) {
        content = contentRef.value.innerHTML;
        allowHTML = true;
    }

    if (content) {
        tooltipInstance.value = tippy(tooltipRef.value as HTMLElement, {
            content: content,
            allowHTML: allowHTML
        });
    }
}

onMounted(_initTooltip)
onUnmounted(_destroyTooltip)


</script>
