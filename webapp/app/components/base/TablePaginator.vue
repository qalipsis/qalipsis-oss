<template>
  <div class="flex items-center justify-between w-full gap-x-1 h-8">
    <div class="flex items-center">
      <BaseTablePaginatorArrow
        type="first"
        :disabled="isFirstPage"
        @click="goFirst"
      />
      <BaseTablePaginatorArrow
        type="prev"
        :disabled="isFirstPage"
        @click="goPrev"
      />
      <template
        v-for="page in pages"
        :key="page"
      >
        <div
          class="w-8 h-8 flex items-center justify-center border border-solid rounded-md mr-2 last:mr-0 cursor-pointer"
          :class="{
            'text-primary-500 border-primary-500': page === currentPageIndex,
            'hover:bg-gray-100 dark:hover:bg-gray-700 border-gray-300 dark:border-gray-600': page !== currentPageIndex,
          }"
          @click="goToPage(page)"
        >
          <span>
            {{ 1 + page }}
          </span>
        </div>
      </template>
      <BaseTablePaginatorArrow
        type="next"
        :disabled="isLastPage"
        @click="goNext"
      />
      <BaseTablePaginatorArrow
        type="last"
        :disabled="isLastPage"
        @click="goLast"
      />
      <div
        v-if="pages.length > 1"
        class="flex items-center mr-2"
      >
        <span class="mx-1">Go to page</span>
        <div>
          <input
            type="text"
            class="w-14 h-8 box-border px-2 outline-none border border-solid border-gray-200 rounded-md dark:bg-gray-900 dark:border-gray-700"
            v-model="targetPage"
            @keyup.enter="handleEnterEvent"
          />
        </div>
      </div>
    </div>
    <div class="pr-2">Showing {{ startItemNumber }} - {{ endItemNumber }} of {{ totalElements }}</div>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    totalElements: number
    pageSize: number
    currentPageIndex: number
    /** Maximum visible page numbers in the paginator. Defaults to 5. */
    maxVisiblePages?: number
  }>(),
  {
    maxVisiblePages: 5,
  },
)
const emit = defineEmits<{
  (e: 'pageChange', v: number): void
}>()

const targetPage = ref('')

/* total pages */
const totalPages = computed(() => Math.ceil(props.totalElements / props.pageSize))

/* page state */
const isFirstPage = computed(() => props.currentPageIndex === 0)
const isLastPage = computed(() => props.currentPageIndex === totalPages.value - 1)

/* item numbers */
const startItemNumber = computed(() => {
  if (props.totalElements === 0) return 0
  return props.currentPageIndex * props.pageSize + 1
})
const endItemNumber = computed(() => Math.min((props.currentPageIndex + 1) * props.pageSize, props.totalElements))

/**
 * Computes the list of page indices that should be visible in the paginator.
 *
 * The paginator shows a maximum of `maxVisiblePages` pages at a time.
 * If the total number of pages is less than or equal to this limit,
 * all pages will be displayed.
 *
 * Otherwise, a sliding window of pages is calculated around the current page:
 *
 * Example (maxVisiblePages = 5):
 *
 * totalPages = 20
 *
 * currentPageIndex = 0
 * -> [0,1,2,3,4]
 *
 * currentPageIndex = 6
 * -> [4,5,6,7,8]
 *
 * currentPageIndex = 19
 * -> [15,16,17,18,19]
 *
 * The window tries to keep the current page centered. When the current page
 * is near the beginning or end of the list, the window shifts accordingly
 * so that the number of visible pages remains constant.
 *
 * @returns Array<number> List of page indices to render in the paginator
 */
const pages = computed(() => {
  const total = totalPages.value
  const current = props.currentPageIndex
  const maxVisible = props.maxVisiblePages

  // Total pages is less than maximum visible pages. Show all pages.
  if (total <= maxVisible) {
    return Array.from({ length: total }, (_, i) => i)
  }

  // Always try to keep the selected page on the middle.
  const half = Math.floor(maxVisible / 2)

  let start = Math.max(current - half, 0)
  let end = Math.min(start + maxVisible - 1, total - 1)

  /**
   * When the current page is close to the last page.
   * E.g.,
   * maxVisiblePages = 5
   * totalPages = 10
   * currentPageIndex = 9
   *
   * half = Math.floor(5 / 2) = 2
   * start = max(9 - 2, 0) = max(7, 0) = 7
   * end = min (7 + 5 - 1, 10 - 1) = min(11, 9) = 9
   *
   * pages = [7, 8, 9] // only 3 pages, not enough for 5 pages.
   *
   * if the number of pages in the window is less than the maximum page,
   * the start page is changed to make sure there are always 5 pages displayed.
   */
  if (end - start + 1 < maxVisible) {
    start = Math.max(end - maxVisible + 1, 0)
  }

  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
})

/* navigation */
const goToPage = (page: number) => {
  if (page < 0 || page >= totalPages.value) return
  emit('pageChange', page)
}

const goFirst = () => goToPage(0)
const goLast = () => goToPage(totalPages.value - 1)
const goPrev = () => goToPage(props.currentPageIndex - 1)
const goNext = () => goToPage(props.currentPageIndex + 1)

/* go to page input */
const handleEnterEvent = () => {
  let page = parseInt(targetPage.value)

  if (!isNaN(page) && page >= 1) {
    if (page > totalPages.value) {
      page = totalPages.value
    }

    goToPage(page - 1)
  }

  targetPage.value = ''
}
</script>
