<template>
  <div class="px-3 rounded-xl flex items-center justify-center h-5 w-fit text-xs"
       :class="[textCssClass, backgroundCssClass]" @click="emit('click')">
    <span>{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  /**
   * The css class for the tag background
   */
  backgroundCssClass: string;
  /**
   * The css class for the text
   */
  textCssClass: string;
  /**
   * The text of the tag
   */
  text: string;
}>()
const emit = defineEmits<{
  (e: 'click'): void
}>();
</script>
