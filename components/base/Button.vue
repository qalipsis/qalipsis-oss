<template>
    <button 
        type="button"
        :class="[
            btnBaseClass,
            TailwindClassHelper.baseButtonClass
        ]"
        :disabled="disabled"
        @click="emit('click')">
        <BaseIcon 
            v-if="icon"
            class="pr-2"
            :class="btnTextClass"
            :icon="icon"
        >
        </BaseIcon>
        <span :class="btnTextClass">
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
const btnBaseClass = computed(() => props.btnStyle === "outlined" ?
    TailwindClassHelper.outlineButtonBaseClass :
    TailwindClassHelper.filledButtonBaseClass
)

const btnTextClass = computed(() => props.btnStyle === "outlined" ?
    TailwindClassHelper.outlineButtonTextClass :
    TailwindClassHelper.filledButtonTextClass
)
</script>
