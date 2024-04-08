<template>
    <div class="mb-3">
        <label class="label" for="reportDescription">Description:</label>
        <a-input
            id="reportDescription"
            v-model:value="description"
            placeholder="Add the description about the report..."
            @change="handleDescriptionValueChange"
        />
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
    /**
     * The concatenated string of the preset campaign patterns
     */
    presetDescription?: string;
}>();

const emit = defineEmits<{
    /**
     * The change description event emitter.
     */
    (e: 'change', value: string): void
}>()

/**
 * The value from the description text input.
 */
const description = ref(props.presetDescription);

/**
 * Prepares the campaign patterns by the pattern input value change event.
 * 
 * @param value The value from the text input.
 */
 const handleDescriptionValueChange = debounce(async () => {
    emit('change', description.value ?? '');
}, 500)

</script>
