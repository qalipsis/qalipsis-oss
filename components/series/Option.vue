<template>
    <div 
        class="flex justify-between"
        :class="TailwindClassHelper.seriesOptionItemClass"
        :style="{ borderColor: isActive ? color : undefined }"
        @click="emits('click')">
        <div class="flex items-baseline">
            <div
                class="w-2 h-2 rounded-full flex-shrink-0"
                :style="{ backgroundColor: color}">
            </div>
            <span class="pl-1 line-clamp-2 text-ellipsis"> {{ displayName }} </span>
        </div>
        <BaseTag
            :text="dataTypeText"
            :background-css-class="'bg-gray-100'"
            :text-css-class="'capitalize'"
        />
    </div>
</template>

<script setup lang="ts">

const props = defineProps<{
    reference: string;
    displayName: string;
    dataType: DataType;
    isActive: boolean;
    color?: string;
}>();
const emits = defineEmits<{
    (e: 'click'): void
}>()

const color = computed(() => props.color ?? ColorsConfig.PRIMARY_COLOR_HEX_CODE);
const dataTypeText = computed(() => props.dataType.toLowerCase())

</script>
