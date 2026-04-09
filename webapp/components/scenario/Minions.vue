<template>
  <BaseTooltip>
    <template #tooltipContent>
      <div>Scheduled minions: {{ scheduledMinions }}</div>
      <div>Started minions: {{ startedMinions }}</div>
      <div>Completed minions: {{ completedMinions }}</div>
    </template>
    <div
      class="flex items-center cursor-pointer gap-x-2"
      @click="handleRowClick"
    >
      <BaseIcon
        icon="qls-icon-two-users-stroke"
        class="text-xl text-gray-500 dark:text-gray-100"
      />
      <div class="grid grid-cols-3 text-sm gap-x-2 text-center">
        <div class="text-gray-700 dark:text-gray-400 leading-3">
          <div>{{ scheduledMinionsText }}</div>
          <div class="text-xs">Scheduled</div>
        </div>
        <div class="text-purple-700 dark:text-purple-400 leading-3">
          <div>{{ startedMinionsText }}</div>
          <div class="text-xs">Started</div>
        </div>
        <div class="text-green-700 dark:text-green-400 leading-3">
          <div>{{ completedMinionsText }}</div>
          <div class="text-xs">Completed</div>
        </div>
      </div>
    </div>
  </BaseTooltip>
  <BaseModal
    v-if="minionsModalOpen"
    v-model:open="minionsModalOpen"
    :title="scenarioName"
    :footer-hidden="true"
    :closable="true"
  >
    <section class="mt-5">
      <div class="flex items-center pb-2">
        <div
          :style="{ backgroundColor: scheduledMinionsColor }"
          class="w-6 h-6 mr-2"
        ></div>
        <div>Scheduled minions: {{ scheduledMinions }}</div>
      </div>
      <div class="flex items-center pb-2">
        <div
          :style="{ backgroundColor: startedMinionsColor }"
          class="w-6 h-6 mr-2"
        ></div>
        <div>Started minions: {{ startedMinions }}</div>
      </div>
      <div class="flex items-center">
        <div
          :style="{ backgroundColor: completedMinionsColor }"
          class="w-6 h-6 mr-2"
        ></div>
        <div>Completed minions: {{ completedMinions }}</div>
      </div>
      <div class="mr-2">
        <apexchart
          height="40"
          width="460"
          :options="chartOptions"
          :series="minionBarChartDataSeries"
        />
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
   * The number of scheduled minions.
   */
  scheduledMinions: number

  /**
   * The number of started minions.
   */
  startedMinions: number

  /**
   * The number of completed minions.
   */
  completedMinions: number

  /**
   * The name of the scenario.
   */
  scenarioName: string
}>()

/**
 * The color of the scheduled minions
 */
const scheduledMinionsColor = ColorsConfig.GREY_2_HEX_CODE

/**
 * The color of the started minions.
 */
const startedMinionsColor = ColorsConfig.PURPLE_COLOR_HEX_CODE

/**
 * The color of the completed minions.
 */
const completedMinionsColor = ColorsConfig.PRIMARY_COLOR_HEX_CODE

const completedMinionsText = computed(() => ScenarioDetailsHelper.toDisplayNumber(props.completedMinions))
const startedMinionsText = computed(() => ScenarioDetailsHelper.toDisplayNumber(props.startedMinions))
const scheduledMinionsText = computed(() => ScenarioDetailsHelper.toDisplayNumber(props.scheduledMinions))

const chartOptions = ScenarioDetailsConfig.MINION_STACKED_BAR_CHART_OPTIONS

const minionBarChartDataSeries = computed(() =>
  ScenarioHelper.toMinionBarChartSeries(props.completedMinions, props.startedMinions, props.scheduledMinions),
)

const minionsModalOpen = ref(false)

const handleRowClick = () => {
  minionsModalOpen.value = true
}
</script>
