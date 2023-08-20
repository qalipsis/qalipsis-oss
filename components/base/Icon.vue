<template>
    <img :src="icon" :class="customClass"/>
</template>

<script setup lang="ts">
defineProps<{
    icon: string;
    customClass?: string;
}>()
</script>