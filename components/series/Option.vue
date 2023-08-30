<template>
    <div 
        class="flex space-between cursor-pointer series-option"
        :class="{ 'series-option--active': isActive }"
        @click="emits('click')">
        <div class="flex name-wrapper">
            <div class="dot"></div>
            <span class="pl-1"> {{ displayName }} </span>
        </div>
        <BaseTag
            :text="dataTypeText"
            :background-css-class="'bg-grey-9'"
            :text-css-class="'text-black capitalize'"
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

const color = computed(() => props.color ?? ColorHelper.PRIMARY_COLOR_HEX_CODE);
const dataTypeText = computed(() => props.dataType.toLowerCase())

</script>

<style scoped lang="scss">
@import "../../assets/scss/color";
@import "../../assets/scss/variables";

.dot {
    width: .5rem;
    height: .5rem;
    border-radius: 50%;
    background-color: v-bind(color);
}

.series-option {
    border: 1px solid $grey-3;
    align-items: baseline;
    border-radius: $default-radius;
    align-items: baseline;
    border-radius: 6px;
    height: $item-height;
    padding: 0.25rem .5rem;
    width: 100%;
    line-height: 14px;

    .name-wrapper {
        align-items: baseline;
    }
    
    &--active {
        border-color: v-bind(color);
    }

    &:hover {
        background-color: rgba(0, 0, 0, 0.06)
    }
}
</style>