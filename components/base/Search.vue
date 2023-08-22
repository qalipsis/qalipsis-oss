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
    placeholder: string;
    collapsable?: boolean;
}>();
const emits = defineEmits<{
    (e: 'search', v: string): void,
}>()
const value = ref('');
const active = ref(props.collapsable === false);

const debouncedSearch = debounce(() => {
    emits('search', value.value);
}, 300);

const handleSearchIconClick = () => {
    if (active.value) {
        emits('search', value.value);
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

$search-input-height: 2.25rem;
$icon-width: 2.25rem;

.search-container {
    display: flex;
    border: 1px solid transparent;
    border-radius: $default-radius;
    height: $search-input-height;
    transition: width .5s;
    align-items: center;
    width: fit-content;

    &--active {
        border-color: $grey-3;
    }

    .icon-wrapper {
        width: $icon-width;
        height: $search-input-height;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .search-input {
        height: 2rem;
        border: none;
        outline: none;
        padding: 0;
        width: 0;
        transition: width 0.3s ease;

        &--active {
            width: 10rem;
            padding: 0 0.5rem;
        }
    }
}

</style>