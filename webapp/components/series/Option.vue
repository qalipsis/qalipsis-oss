<template>
  <div
      class="flex justify-between items-center border border-solid align-baseline border-gray-200 rounded-md h-10 py-1 px-2 w-full cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800"
      :style="{ borderColor: isActive ? color : undefined }"
      @click="emits('click')"
  >
    <div class="flex items-center w-full overflow-hidden">
      <div
          class="w-2 h-2 rounded-full flex-shrink-0"
          :style="{ backgroundColor: color}">
      </div>
      <div class="pl-1 w-full overflow-hidden">
        <BaseTooltip
            :text="displayName"
            :show-tooltip-if-truncated="true"
        >
          <span class="text-sm"> {{ displayName }} </span>
        </BaseTooltip>
      </div>
    </div>
    <BaseTag
        :text="dataTypeText"
        :background-css-class="'bg-gray-100 dark:bg-gray-700'"
        :text-css-class="'capitalize dark:text-gray-100'"
    />
  </div>
</template>

<script setup lang="ts">

const props = defineProps<{
  reference: string;
  displayName: string;
  dataType: DataType;
  isActive: boolean;
  color?: string;
}>();
const emits = defineEmits<{
  (e: 'click'): void
}>()

const color = computed(() => props.color ?? ColorsConfig.PRIMARY_COLOR_HEX_CODE);
const dataTypeText = computed(() => props.dataType.toLowerCase())

</script>
