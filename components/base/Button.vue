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
            class="w-6 h-6 pr-2"
            :icon="icon"
            :class="[
                disabled ? TailwindClassHelper.grayColorFilterClass : '',
                btnIconClass
            ]"
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

const btnIconClass = computed(() => props.btnStyle === "outlined" ?
    TailwindClassHelper.outlineButtonIconClass :
    TailwindClassHelper.filledButtonIconClass
)

const btnTextClass = computed(() => props.btnStyle === "outlined" ?
    TailwindClassHelper.outlineButtonTextClass :
    TailwindClassHelper.filledButtonTextClass
)
</script>
