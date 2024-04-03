<template>
    <template v-if="!isEditing">
        <div class="flex items-center">
            <h1 class="font-medium text-2xl">{{ content }}</h1>
            <div 
                v-if="editable" 
                class="cursor-pointer px-1 py-1 hover:[filter:brightness(0%)_saturate(100%)_invert(61%)_sepia(38%)_saturate(657%)_hue-rotate(132deg)_brightness(89%)_contrast(91%)]"
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
                class="border border-solid border-primary-green-400 rounded-md outline-none p-2"
                @keydown.esc="handleEscKeyDown()"
                @keydown.enter="handleEnterKeyDown()"
            >
            <div class="text-lg text-gray-300 absolute bottom-0 right-2">↵</div>
        </div>
    </template>
    <!-- <a-typography-paragraph :editable="editable" class="editable-title">
        <template #editableIcon>
            <div>
                <BaseIcon
                    icon="/icons/icon-edit.svg"
                    class="cursor-pointer"
                />
            </div>
        </template>
    </a-typography-paragraph> -->
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
    console.log(editingText.value)
    emit('update:content', editingText.value);
}

</script>
