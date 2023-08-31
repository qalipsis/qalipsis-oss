<template>
    <div class="flex items-center">
        <div class="flex items-center cursor-pointer" @click="handleClick">
            <BaseIcon
                icon="/icons/icon-check-grey.svg"
                :class="{ 'icon--active': showChecked}"
            />
            <span 
                :class="{ 'text-primary-color':  showChecked}"
                class="text-grey-1 text-sm"
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

<style scoped>

</style>