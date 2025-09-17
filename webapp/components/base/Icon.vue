<template>
  <span :class="icon"></span>
</template>

<script setup lang="ts">
defineProps<{
  icon: string;
  customClass?: string;
}>()
</script>