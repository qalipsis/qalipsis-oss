<template>
    <template v-if="!isEditing">
        <div class="flex items-center">
            <h1 class="font-medium text-2xl">{{ content }}</h1>
            <div 
                v-if="editable" 
                class="cursor-pointer px-1 pt-1"
                :class="TailwindClassHelper.primaryColorFilterHoverClass"
                @click="isEditing = true"
            >
                <BaseIcon icon="/icons/icon-edit.svg"/>
            </div>
        </div>
    </template>
    <template v-else>
        <div class="relative">
            <input 
                type="text"
                v-model="editingText"
                class="border border-solid text-xl border-primary-400 rounded-md outline-none p-2"
                @keydown.esc="handleEscKeyDown()"
                @keydown.enter="handleEnterKeyDown()"
            >
            <div class="text-lg text-gray-300 absolute bottom-0 right-2">↵</div>
        </div>
    </template>
</template>

<script setup lang="ts">
const props = defineProps<{
    editable?: boolean,
    content: string
}>()

const emit = defineEmits<{
  (e: 'update:content', v: string): void;
}>();

const isEditing = ref(false);
const editingText = ref(props.content);

const handleEscKeyDown = () => {
    isEditing.value = false;
    editingText.value = props.content;
}

const handleEnterKeyDown = () => {
    isEditing.value = false;
    emit('update:content', editingText.value);
}

</script>
