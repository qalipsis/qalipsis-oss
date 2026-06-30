<template>
  <BaseTooltip>
    <template #tooltipContent>
      <div>Total Steps: {{ totalExecutions }}</div>
      <div>Successful steps: {{ successfulExecutions }}</div>
      <div>Failed steps: {{ failedExecutions }}</div>
    </template>
    <div
      class="flex items-center cursor-pointer"
      @click="handleRowClick"
    >
      <div v-if="status !== ExecutionStatusConstant.IN_PROGRESS">
        <apexchart
          :options="executionStepDonutChartOptions"
          :series="executionStepDonutChartDataSeries"
          :width="28"
          :height="28"
        />
      </div>
      <div
        v-else
        class="w-7 h-7 rounded-full bg-gray-100 animate-pulse"
      ></div>
      <div class="grid grid-cols-3 text-sm gap-x-2 ml-2 text-center">
        <div class="text-green-700 dark:text-green-400 leading-3">
          <div>{{ successfulExecutionText }}</div>
          <div v-if="showLabels" class="text-xs">Successes</div>
        </div>
        <div class="text-red-700 dark:text-red-400 leading-3">
          <div>{{ failedExecutionText }}</div>
          <div v-if="showLabels" class="text-xs">Failures</div>
        </div>
        <div class="text-gray-700 dark:text-gray-400 leading-3">
          <div>{{ totalExecutionText }}</div>
          <div v-if="showLabels" class="text-xs">Total</div>
        </div>
      </div>
    </div>
  </BaseTooltip>
  <BaseModal
    v-if="executionStepModalOpen"
    v-model:open="executionStepModalOpen"
    :title="scenarioName"
    :footer-hidden="true"
    :closable="true"
  >
    <section class="flex items-center mt-5">
      <apexchart
        :options="executionStepDonutChartOptions"
        :series="executionStepDonutChartDataSeries"
        :width="280"
        :height="280"
      />
      <div class="ml-4">
        <div class="flex items-center mb-2">
          <div>Total Steps: {{ totalExecutions }}</div>
        </div>
        <div class="flex items-center mb-2">
          <div
            :style="{ backgroundColor: successfulExecutionsColor }"
            class="w-6 h-6 mr-2"
          ></div>
          <div>Successful steps: {{ successfulExecutions }}</div>
        </div>
        <div class="flex items-center">
          <div
            :style="{ backgroundColor: failedExecutionsColor }"
            class="w-6 h-6 mr-2"
          ></div>
          <div>Failed steps: {{ failedExecutions }}</div>
        </div>
      </div>
    </section>
  </BaseModal>
</template>

<script setup lang="ts">
const props = defineProps<{
  /**
   * The execution status of a campaign
   */
  status: ExecutionStatus
  /**
   * Number of successful executions.
   */
  successfulExecutions: number

  /**
   * Number of failed executions.
   */
  failedExecutions: number

  /**
   * The name of the scenario.
   */
  scenarioName: string

  /**
   * Whether to show Success / Failed / Total labels below the numbers. Defaults to true.
   */
  showLabels?: boolean
}>()

const executionStepDonutChartOptions = ScenarioDetailsConfig.EXECUTION_STEP_DONUT_CHART_OPTIONS

const successfulExecutionsColor = ColorsConfig.PRIMARY_COLOR_HEX_CODE
const failedExecutionsColor = ColorsConfig.PINK_HEX_CODE

/**
 * The number of total executions.
 */
const totalExecutions = computed(() => props.successfulExecutions + props.failedExecutions)

const successfulExecutionText = computed(() => ScenarioDetailsHelper.toDisplayNumber(props.successfulExecutions))
const failedExecutionText = computed(() => ScenarioDetailsHelper.toDisplayNumber(props.failedExecutions))
const totalExecutionText = computed(() => ScenarioDetailsHelper.toDisplayNumber(totalExecutions.value))

/**
 * The data series for the donut chart.
 */
const executionStepDonutChartDataSeries = computed(() => [props.successfulExecutions, props.failedExecutions])

const executionStepModalOpen = ref(false)

const handleRowClick = () => {
  executionStepModalOpen.value = true
}
</script>
