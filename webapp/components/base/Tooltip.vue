<template>
  <div
    ref="tooltipRef"
    :class="ellipsisClass"
  >
    <slot></slot>
  </div>
  <div
    v-if="$slots.tooltipContent"
    ref="contentRef"
    class="hidden"
  >
    <slot name="tooltipContent"></slot>
  </div>
</template>

<script setup lang="ts">
import type { Instance } from 'tippy.js'
import tippy from 'tippy.js'
import 'tippy.js/dist/tippy.css'

const props = defineProps<{
  text?: string
  position?: string
  showTooltipIfTruncated?: boolean
}>()

const tooltipRef = ref<HTMLDivElement>()
const contentRef = ref<HTMLDivElement>()
const tooltipInstance = ref<Instance>()

const ellipsisClass = computed(() =>
  props.showTooltipIfTruncated ? 'text-ellipsis overflow-hidden whitespace-nowrap' : '',
)

const _destroyTooltip = () => {
  if (tooltipInstance.value) {
    tooltipInstance.value.destroy()
  }
}

const _initTooltip = () => {
  _destroyTooltip()

  if (props.showTooltipIfTruncated) {
    if (!(tooltipRef.value && tooltipRef.value.scrollWidth > tooltipRef.value.clientWidth)) return
  }

  if (contentRef.value) {
    const clone = contentRef.value.cloneNode(true) as HTMLElement
    clone.classList.remove('hidden')
    _renderTooltip(clone)
  } else {
    _renderTooltip(props.text)
  }
}

const _renderTooltip = (content: string | HTMLElement | undefined) => {
  if (!content) return

  tooltipInstance.value = tippy(tooltipRef.value as HTMLElement, { content })
}

watch(() => props.text, _initTooltip)
onMounted(_initTooltip)
onUnmounted(_destroyTooltip)
</script>
