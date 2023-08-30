<template>
    <a-tooltip>
        <template #title>
            <div>Total Steps: {{ totalExecutions }}</div>
            <div>Successful steps: {{ successfulExecutions }}</div>
            <div>Failed steps: {{ failedExecutions }}</div>
        </template>
        <div class="flex items-center cursor-pointer" @click="handleRowClick">
            <BaseIcon icon="/icons/icon-chart-light-grey.svg" />
            <apexchart
                :options="executionStepDonutChartOptions"
                :series="executionStepDonutChartDataSeries"
                :width="24"
                :height="24"
            />
            <span class="text-grey-1">
                {{ successfulExecutions }}/{{ failedExecutions }}/{{ totalExecutions }}
            </span>
        </div>
    </a-tooltip>
    <a-modal
        v-if="executionStepModalOpen"
        v-model:open="executionStepModalOpen"
        title="Execution steps"
        :footer="null"
        :closable="true">
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
                    <div :style="{ backgroundColor: successfulExecutionsColor}" class="color-legend mr-2"></div>
                    <div>Successful steps: {{ successfulExecutions }}</div>
                </div>
                <div class="flex items-center">
                    <div :style="{ backgroundColor: failedExecutionsColor}" class="color-legend mr-2"></div>
                    <div>Failed steps: {{ failedExecutions }}</div>
                </div>
            </div>
        </section>
    </a-modal>
</template>

<script setup lang="ts">
const props = defineProps<{
  /**
   * Number of successful executions.
   */
  successfulExecutions: number,

  /**
   * Number of failed executions.
   */
  failedExecutions: number,

  /**
   * The name of the scenario.
   */
  scenarioName: string
}>();

const executionStepDonutChartOptions = ScenarioHelper.EXECUTION_STEP_DONUT_CHART_OPTIONS;

const successfulExecutionsColor = ColorHelper.PRIMARY_COLOR_HEX_CODE;
const failedExecutionsColor = ColorHelper.PINK_HEX_CODE;

/**
 * The number of total executions.
 */
const totalExecutions = computed(() => props.successfulExecutions + props.failedExecutions);

/**
 * The data series for the donut chart.
 */
const executionStepDonutChartDataSeries =  computed(() => [props.successfulExecutions, props.failedExecutions]);

const executionStepModalOpen = ref(false)

const handleRowClick = () => {
    executionStepModalOpen.value = true
}


</script>

<style scoped lang="scss">

</style>