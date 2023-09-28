<template>
    <BaseDrawer
        title="Configuration of campaign"
        :open="open"
        @close="emits('update:open', false)"
    >
        <form>
            <span></span>

        </form>
        <BaseCircleCheckbox
            text="1"
            v-model:active="active"
        />
    </BaseDrawer>
</template>

<script setup lang="ts">
const props = defineProps<{
    open: boolean,
}>();
const emits = defineEmits<{
    (e: "update:open", v: boolean): void
}>();
const active = ref(false)

const timeoutOptions = SeriesHelper.getTimeSeriesTypeOptions();


</script>

<style scoped lang="scss">

</style>