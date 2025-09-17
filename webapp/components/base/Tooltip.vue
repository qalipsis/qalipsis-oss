<template>
  <div ref="tooltipRef" :class="ellipsisClass">
    <slot></slot>
  </div>
  <div v-if="$slots.tooltipContent" ref="contentRef" class="hidden">
    <slot name="tooltipContent"></slot>
  </div>
</template>

<script setup lang="ts">
import type {Instance} from 'tippy.js';
import tippy from 'tippy.js';
import 'tippy.js/dist/tippy.css';

const props = defineProps<{
  text?: string;
  position?: string;
  showTooltipIfTruncated?: boolean;
}>()

const tooltipRef = ref<HTMLDivElement>();
const contentRef = ref<HTMLDivElement>();
const tooltipInstance = ref<Instance>();

const ellipsisClass = computed(() => props.showTooltipIfTruncated
    ? 'text-ellipsis overflow-hidden whitespace-nowrap'
    : '')

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

  if (props.showTooltipIfTruncated) {
    if (tooltipRef.value && tooltipRef.value.scrollWidth > tooltipRef.value.clientWidth) {
      _renderTooltip(content, allowHTML);
    }
  } else {
    _renderTooltip(content, allowHTML);
  }

}

const _renderTooltip = (content: string | undefined, allowHTML: boolean) => {
  if (!content) return;

  tooltipInstance.value = tippy(tooltipRef.value as HTMLElement, {
    content: content,
    allowHTML: allowHTML
  });
}

onMounted(_initTooltip)
onUnmounted(_destroyTooltip)


</script>
