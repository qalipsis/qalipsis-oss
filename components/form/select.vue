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
    /**
     * The initial model value to be bound.
     */
    modelValue: string,
    /**
     * The options for the dropdown menu.
     */
    options: FormDropdownOption[]
}>();

const emit = defineEmits<{
    (e: 'update:modelValue', v: string): void
}>();

const value = ref(props.modelValue);

</script>
