<template>
  <img
      class="animate-spin"
      :class="{
            'w-12 h-12': size === 'lg',
            'w-10 h-10': size === 'md',
            'w-8 h-8': size === 'sm',
        }"
      src="/icons/icon-spinner.svg"
      alt="Loading icon"
  >
</template>

<script setup lang="ts">
withDefaults(defineProps<{ size?: 'sm' | 'md' | 'lg' }>(), {
  size: 'sm',
});
</script>

<style scoped></style>
