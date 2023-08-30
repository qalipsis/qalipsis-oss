<template>
    <a-tag class="base-tag" :class="[textCssClass, backgroundCssClass]" @click="emit('click')">
        {{ text }}
    </a-tag>
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

<style scoped lang="scss">
.base-tag {
    border: none;
    padding: 0 .75rem;
    border-radius: 10px;
    margin: 0;
}
</style>