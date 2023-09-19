<template>
    <a-button 
        :class="defaultBtnClass"
        :disabled="disabled"
        @click="emit('click')">
        <BaseIcon v-if="icon" :icon="icon" :class="{ 'icon-disabled': disabled }">
        </BaseIcon>
        <span :class="{ 'text-grey-1': disabled }">
            {{ text }}
        </span>
    </a-button>
</template>

<script setup lang="ts">
const props = defineProps<{
    text: string,
    btnStyle?: "stroke" | "default",
    icon?: string,
    disabled?: boolean
}>();
const emit = defineEmits<{
    (e: "click"): void
}>()
const defaultBtnClass = computed(() => props.btnStyle === "stroke" ?
    "base-stroke-btn" :
    "base-btn"
)
</script>

<style scoped lang="scss">
@import "../../assets/scss/color";
@import "../../assets/scss/mixins";

.base-btn {
    @include button;

    &:not([disabled]) {
        background-color: $primary-color;
        color: $white;
        border: none;
        transition: background-color .2s ease-in-out;
        
        img {
            filter: $white-svg;
        }

        &:hover {
            background-color: $primary-color-medium !important;
            color: $white !important;
        }

    }
}

.base-stroke-btn {
    @include button;
    border: 1px solid $grey-2;
    color: $black;
    transition: border-color .2s ease-in-out;

    &:not([disabled]) {
        img {
            filter: $black-svg;
        }
        
        &:hover {
            color: $primary-color-medium;
            border-color: $primary-color-medium;
            
            img {
                filter: $primary-color-svg;
            }
        }
    }
}

</style>