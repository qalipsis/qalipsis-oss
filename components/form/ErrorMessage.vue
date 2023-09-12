<template>
    <span v-if="errorMessage" class="text-xs text-pink">{{ errorMessage }}</span>
</template>

<script setup lang="ts">
defineProps<{
    errorMessage?: string
}>()
</script>
