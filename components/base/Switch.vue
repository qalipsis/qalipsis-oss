<template>
    <div class="flex items-center">
        <div 
            class="flex items-center cursor-pointer hover:text-primary-500" 
            :class="[showChecked ? 'text-primary-500' : 'text-gray-500']"
            @click="handleClick"
        >
            <BaseIcon
                icon="qls-icon-check-fill"
                class="text-2xl"
            />
            <span 
                class="text-sm"
            >Show {{ numberOfSelectedItems }} selected items</span>
        </div>
    </div>
</template>

<script setup lang="ts">
defineProps<{
    numberOfSelectedItems: number; 
}>()
const showChecked = ref(false);
const emit = defineEmits<{
    (e: "checkedChange", v: boolean): void
}>()

const handleClick = () => {
    showChecked.value = !showChecked.value;
    emit("checkedChange", showChecked.value);
}

</script>
