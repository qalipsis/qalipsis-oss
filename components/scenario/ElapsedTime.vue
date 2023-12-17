<template>
    <div class="flex items-center">
        <BaseIcon icon="/icons/icon-time.svg" />
        <span class="text-grey-1 pl-1">{{ displayTimeText }}</span>
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
    start: string,
    end?: string
}>()

/**
 * The display time text
 */
 const displayTimeText = computed(() => ScenarioHelper.toTimeDisplayText(props.start, props.end));

</script>

<style scoped lang="scss">

</style>