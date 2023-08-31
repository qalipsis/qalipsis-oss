<template>
    <a-typography-paragraph :editable="editable" class="editable-title">
        <template #editableIcon>
            <div>
                <BaseIcon
                    icon="/icons/icon-edit.svg"
                    class="cursor-pointer"
                />
            </div>
        </template>
    </a-typography-paragraph>
</template>

<script setup lang="ts">
const props = defineProps<{
    editable?: boolean
}>()

const editable = props.editable
    ? {  triggerType: ['icon', 'text'], tooltip: false, autoSize: { maxRows: 1, minRows: 1 } }
    : false

</script>
