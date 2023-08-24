<template>
    <a-button 
        :class="defaultBtnClass"
        :disabled="disabled"
        @click="emit('click')">
        <template v-if="icon">
            <img :src="icon" />
        </template>
        <span>
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
@import "../../assets/scss/variables";


@mixin button {
    height: 2.75rem;
    padding: 0.75rem 0.5rem;
    font-size: 1rem;
    border-radius: $default-radius;
    min-width: 8.5rem;
    display: flex;
    align-items: center;
    justify-content: center;

    img {
        width: 1.5rem;
        height: 1.5rem;
        padding-right: 0.5rem;
    }

    span {
        color: inherit;
        line-height: 1.5rem;
    }
}

.base-btn {
    @include button;
    background-color: $primary-color;
    color: $white;
    border: none;
    transition: background-color .2s ease-in-out;

    img {
        filter: $white-svg;
    }

    &:not([disabled]):hover {
        background-color: $primary-color-medium !important;
        color: $white !important;
    }
}

.base-stroke-btn {
    @include button;
    border: 1px solid $grey-2;
    color: $black;
    transition: border-color .2s ease-in-out;

    img {
        filter: $black-svg;
    }

    &:not([disabled]):hover {
        color: $primary-color-medium;
        border-color: $primary-color-medium;

        img {
            filter: $primary-color-svg;
        }

    }
}

</style>