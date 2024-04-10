<template>
    <div class="mb-2">
        <label class="font-medium text-gray-700 text-base">{{ text }}</label>
    </div>
</template>

<script setup lang="ts">
defineProps<{
    text: string,
}>();

</script>
