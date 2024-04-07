<template>
    <div :class="[{ 'border-gray-300': active, 'border-transparent': !active}, TailwindClassHelper.searchInputBaseClass]">
        <input
            class="h-9 border-none outline-none transition-width duration-300 rounded-md"
            :class="active ? 'w-40 px-2' : 'p-0 w-0'"
            type="text"
            v-model="value"
            :placeholder="placeholder"
            @input="handleTextInputChange">
        <div class="w-9 h-10 flex items-center justify-center cursor-pointer" @click="handleSearchIconClick">
            <BaseIcon icon="/icons/icon-search.svg" />
        </div>
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
    placeholder: string;
    collapsable?: boolean;
}>();
const emit = defineEmits<{
    (e: "search", v: string): void,
}>()
const value = ref('');
const active = ref(props.collapsable === false);

const debouncedSearch = debounce(() => {
    if (props.collapsable && !value.value) {
        active.value = false;
    }

    emit('search', value.value);
}, 300);

const handleSearchIconClick = () => {
    if (active.value) {
        emit('search', value.value);
    } else {
        active.value = true;
    }
}

const handleTextInputChange = () => {
    debouncedSearch();
}
</script>
