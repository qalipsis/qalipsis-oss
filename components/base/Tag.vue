<template>
    <div class="px-3 py-0.5 rounded-xl w-fit text-xs" :class="[textCssClass, backgroundCssClass]" @click="emit('click')">
        {{ text }}
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
