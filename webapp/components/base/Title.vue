<template>
  <template v-if="!isEditing">
    <div class="flex items-center">
      <h1 class="font-medium text-2xl dark:text-gray-100">{{ content }}</h1>
      <div
        v-if="editable"
        class="cursor-pointer px-1 pt-2"
        @click="startEditing"
        title="Edit"
        aria-label="Edit content"
      >
        <BaseIcon
          icon="qls-icon-edit"
          class="text-2xl text-gray-700 dark:text-gray-100 hover:text-primary-500"
        />
      </div>
    </div>
  </template>

  <template v-else>
    <div class="relative">
      <input
        type="text"
        v-model="editingText"
        class="border border-solid text-xl border-primary-400 bg-transparent rounded-md outline-none p-2"
        @keydown.esc="handleEscKeyDown"
        @keydown.enter="handleEnterKeyDown"
        @blur="handleBlur"
        ref="inputRef"
      />
      <div class="text-lg text-gray-300 absolute bottom-0 right-2 pointer-events-none">↵</div>
    </div>
  </template>
</template>

<script setup lang="ts">
const props = defineProps({
  editable: { type: Boolean, default: false },
  content: { type: String, required: true },
})

const emit = defineEmits<{
  (e: 'update:content', v: string): void
}>()

// --- State ---
const isEditing = ref(false)
const editingText = ref(props.content)
const inputRef = ref<HTMLInputElement | null>(null)

// --- Start editing ---
const startEditing = () => {
  if (!props.editable) return

  isEditing.value = true
  // Focus and select the text.
  nextTick(() => {
    inputRef.value?.focus()
    inputRef.value?.select()
  })
}

// --- Handlers ---
const handleEscKeyDown = (e: KeyboardEvent) => {
  if (e.key !== 'Escape') return
  e.preventDefault()
  isEditing.value = false
  editingText.value = props.content
}

const handleEnterKeyDown = () => {
  if (editingText.value.trim() === '') return

  isEditing.value = false
  emit('update:content', editingText.value)
}

const handleBlur = () => {
  handleEnterKeyDown()
}
</script>
