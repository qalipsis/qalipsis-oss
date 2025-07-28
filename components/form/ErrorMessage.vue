<template>
  <span
    v-if="errorMessage"
    class="text-xs text-red-700"
    >{{ errorMessage }}</span
  >
</template>

<script setup lang="ts">
defineProps<{
  errorMessage?: string
}>()
</script>
