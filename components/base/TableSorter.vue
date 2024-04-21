<template>
    <div class="flex flex-col w-2 h-3 justify-between">
        <span 
            class="w-0 h-0 border-r-4 border-r-transparent border-l-4 border-l-transparent border-b-4 border-solid"
            :class="{
                'border-b-gray-500 group-hover:border-b-gray-800': (activeSorterKey !== sorterKey) || (activeSorterKey === sorterKey && activeSorterDirection !== 'asc'),
                'border-b-primary-500': activeSorterKey === sorterKey && activeSorterDirection === 'asc',
            }"
        ></span>
        <span 
            class="w-0 h-0 border-r-4 border-r-transparent border-l-4 border-l-transparent border-t-4 border-solid"
            :class="{
                'border-t-gray-500 group-hover:border-t-gray-800': (activeSorterKey !== sorterKey) || (activeSorterKey === sorterKey && activeSorterDirection !== 'desc'),
                'border-t-primary-500': activeSorterKey === sorterKey && activeSorterDirection === 'desc',
            }"
        >
        </span>
    </div>
</template>

<script setup lang="ts">

defineProps<{
    sorterKey: string;
    activeSorterKey: string;
    activeSorterDirection: string;
}>()

</script>
