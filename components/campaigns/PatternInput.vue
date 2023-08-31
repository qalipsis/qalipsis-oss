
<template>
    <div>
        <label class="label" for="campaignPatternInput">Campaign patterns:</label>
        <a-input 
            v-model:value="campaignPatternInput"
            placeholder="Type your patterns here, separated by commas..."
            @change="handlePatternsValueChange"
        />
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
    /**
     * The concatenated string of the preset campaign patterns
     */
    presetCampaignPatterns?: string;
}>();

const emit = defineEmits<{
    /**
     * The change campaign patterns event emitter.
     */
    (e: 'campaignPatternsChange', value: string[]): void
}>()

/**
 * The value from the campaign pattern text input.
 */
const campaignPatternInput = ref(props.presetCampaignPatterns);

/**
 * Prepares the campaign patterns by the pattern input value change event.
 * 
 * @param value The value from the text input.
 */
const handlePatternsValueChange = debounce(async () => {
    let campaignPatterns: string[] = [];
    if (campaignPatternInput.value) {
        campaignPatterns = campaignPatternInput.value
            .split(',')
            .filter(pattern => pattern)
            .map(pattern => pattern.trim());
    }
    emit('campaignPatternsChange', campaignPatterns);
}, 500)

</script>
