<template>
    <div class="flex items-center w-full justify-between">
        <div class="flex items-center">
          <div
            class="w-8 h-8 rounded-md flex items-center justify-center mr-2"
            :class="{
              'hover:bg-gray-100 cursor-pointer': !leftArrowDisabled,
              'cursor-not-allowed': leftArrowDisabled,
            }"
            @click="!leftArrowDisabled && handlePageNumberClick(currentPageIndex - 1)"
          >
            <div
              class="border-r-2 border-b-2 -mr-1 border-solid p-1 rotate-[135deg]"
              :class="{
                'border-gray-900': !leftArrowDisabled,
                'border-gray-400': leftArrowDisabled,
              }"
            ></div>
          </div>
          <template v-for="page in pages" :key="page">
            <div
              class="w-8 h-8 flex items-center justify-center border border-solid rounded-md border-gray-300 mr-2 last:mr-0 cursor-pointer"
              :class="{
                'text-primary-500 border-primary-500': page === currentPageIndex,
                'hover:bg-gray-100': page !== currentPageIndex,
              }"
              @click="handlePageNumberClick(page)"
            >
              <span>
                {{ 1 + page }}
              </span>
            </div>
          </template>
          <div
            class="w-8 h-8 rounded-md flex items-center justify-center"
            :class="{
              'hover:bg-gray-100 cursor-pointer': !rightArrowDisabled,
              'cursor-not-allowed': rightArrowDisabled,
            }"
            @click="
              !rightArrowDisabled && handlePageNumberClick(currentPageIndex + 1)
            "
          >
            <div
              class="border-r-2 border-b-2 -ml-1 border-solid p-1 -rotate-45"
              :class="{
                'border-gray-900': !rightArrowDisabled,
                'border-gray-400': rightArrowDisabled,
              }"
            ></div>
          </div>
          <div 
            v-if="pages.length > 1"
            class="flex items-center"
          >
            <span class="mx-2">Go to page</span>
            <div>
              <input
                type="text"
                class="w-14 ml-1 py-1 px-2 outline-none border border-solid border-gray-200 rounded-md"
                v-model="targetPage"
                @keyup.enter="handleEnterEvent"
              />
            </div>
          </div>
        </div>
        <div class="pr-2">
          Showing {{ startItemNumber }} - {{ endItemNumber }}
          of {{ totalElements }}
        </div>
    </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  totalElements: number;
  pageSize: number;
  currentPageIndex: number;
}>();
const emit = defineEmits<{
  (e: "pageChange", v: number): void;
}>();

const targetPage = ref("");

const numberOfTotalPages = computed(() =>
  Math.ceil(props.totalElements / props.pageSize)
);

const startItemNumber = computed(() => props.currentPageIndex * props.pageSize + 1);
const endItemNumber = computed(() => (props.currentPageIndex + props.pageSize) <= props.totalElements
    ? props.currentPageIndex + props.pageSize
    : props.totalElements
);

const leftArrowDisabled = computed(() => props.currentPageIndex === 0);
const rightArrowDisabled = computed(
  () => props.currentPageIndex === numberOfTotalPages.value - 1
);

const pages = computed(() => {
  const numberOfPages = Math.ceil(props.totalElements / props.pageSize);

  return Array.from(Array(numberOfPages).keys());
});

const handleEnterEvent = () => {
  let targetPageNumber = parseInt(targetPage.value);

  if (
    !isNaN(targetPageNumber) &&
    Number.isInteger(targetPageNumber) &&
    targetPageNumber >= 1
  ) {
    if (targetPageNumber >= numberOfTotalPages.value) {
      targetPageNumber = numberOfTotalPages.value;
    }
    const targetPageIndex = targetPageNumber - 1;
    handlePageNumberClick(targetPageIndex);
  }
  targetPage.value = "";
};

const handlePageNumberClick = (pageIndex: number) => {
  emit("pageChange", pageIndex);
};
</script>
