<template>
    <BaseTooltip>
        <template #tooltipContent>
            <div>Total Steps: {{ totalExecutions }}</div>
            <div>Successful steps: {{ successfulExecutions }}</div>
            <div>Failed steps: {{ failedExecutions }}</div>
        </template>
        <div class="flex items-center cursor-pointer" @click="handleRowClick">
            <BaseIcon icon="qls-icon-chart-stroke" class="text-xl text-gray-500" />
            <apexchart
                :options="executionStepDonutChartOptions"
                :series="executionStepDonutChartDataSeries"
                :width="24"
                :height="24"
            />
            <span class="text-gray-500">
                {{ successfulExecutions }}/{{ failedExecutions }}/{{ totalExecutions }}
            </span>
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
                    <div :style="{ backgroundColor: successfulExecutionsColor}" class="w-6 h-6 mr-2"></div>
                    <div>Successful steps: {{ successfulExecutions }}</div>
                </div>
                <div class="flex items-center">
                    <div :style="{ backgroundColor: failedExecutionsColor}" class="w-6 h-6 mr-2"></div>
                    <div>Failed steps: {{ failedExecutions }}</div>
                </div>
            </div>
        </section>
    </BaseModal>
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

const executionStepDonutChartOptions = ScenarioDetailsConfig.EXECUTION_STEP_DONUT_CHART_OPTIONS;

const successfulExecutionsColor = ColorsConfig.PRIMARY_COLOR_HEX_CODE;
const failedExecutionsColor = ColorsConfig.PINK_HEX_CODE;

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
