<template>
    <div class="search-container" :class="{ 'search-container--active': active}">
        <input
            class="search-input"
            :class="{ 'search-input--active': active}"
            type="text"
            v-model="value"
            :placeholder="placeholder"
            @input="handleTextInputChange">
        <div class="icon-wrapper cursor-pointer" @click="handleSearchIconClick">
            <BaseIcon icon="/icons/icon-search.svg" />
        </div>
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
    // FIXME: modelValue can be removed
    modelValue: string;
    placeholder: string;
    collapsable?: boolean;
}>();
const emit = defineEmits<{
    (e: "update:modelValue", value: string): void,
    (e: "search", v: string): void,
}>()
const value = ref(props.modelValue);
const active = ref(props.collapsable === false);

const debouncedSearch = debounce(() => {
    if (props.collapsable && !value.value) {
        active.value = false;
    }
    emit('update:modelValue', value.value);
    emit('search', value.value);
}, 300);

const handleSearchIconClick = () => {
    if (active.value) {
        emit('update:modelValue', value.value);
        emit('search', value.value);
    } else {
        active.value = true;
    }
}

const handleTextInputChange = () => {
    debouncedSearch();
}
</script>

<style scoped lang="scss">
@import "../../assets/scss/color";
@import "../../assets/scss/variables";

.search-container {
    display: flex;
    border: 1px solid transparent;
    border-radius: $default-radius;
    height: $item-height;
    transition: width .5s;
    align-items: center;
    width: fit-content;

    &--active {
        border-color: $grey-3;
    }

    .icon-wrapper {
        width: 2.25rem;
        height: $item-height;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .search-input {
        height: 2.25rem;
        border: none;
        outline: none;
        padding: 0;
        width: 0;
        transition: width 0.3s ease;
        border-radius: $default-radius; 
        &--active {
            width: 10rem;
            padding: 0 0.5rem;
        }
    }
}

</style>