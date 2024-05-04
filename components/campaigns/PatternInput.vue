
<template>
    <FormInput
        form-control-name="patternText"
        label="Campaign patterns"
        placeholder="Type your patterns here, separated by commas..."
        v-model="campaignPatternInput"
        @input="handlePatternsValueChange"
    ></FormInput>
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
            .map(pattern => pattern.trim())
            .filter(pattern => pattern);
    }
    emit('campaignPatternsChange', campaignPatterns);
}, 500)

</script>
