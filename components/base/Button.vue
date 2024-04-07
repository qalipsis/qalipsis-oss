<template>
    <button 
        type="button"
        :class="[
            defaultBtnClass,
            TailwindClassHelper.baseButtonClass
        ]"
        :disabled="disabled"
        @click="emit('click')">
        <BaseIcon 
            v-if="icon"
            class="w-6 h-6 pr-2"
            :icon="icon"
            :class="disabled ? TailwindClassHelper.grayColorFilterClass : ''"
        >
        </BaseIcon>
        <span>
            {{ text }}
        </span>
    </button>
</template>

<script setup lang="ts">
const props = defineProps<{
    text: string,
    btnStyle?: "outlined" | "filled",
    icon?: string,
    disabled?: boolean
}>();
const emit = defineEmits<{
    (e: "click"): void
}>()
const defaultBtnClass = computed(() => props.btnStyle === "outlined" ?
    TailwindClassHelper.outlineButtonClass :
    TailwindClassHelper.filledButtonClass
)
</script>
