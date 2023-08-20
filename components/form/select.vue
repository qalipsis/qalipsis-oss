<template>
    <a-select 
        size="large"
        v-model:value="value"
        class="full-width"
        :options="options"
        @change="emit('update:modelValue', value)">
    </a-select>
</template>

<script setup lang="ts">    
const props = defineProps<{
    modelValue: string,
    options: FormDropdownOption[]
}>();

const emit = defineEmits<{
    (e: 'update:modelValue', v: string): void
}>();

const value = ref(props.modelValue);

</script>
