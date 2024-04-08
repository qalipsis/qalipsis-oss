<template>
    <a-tooltip>
        <template #title>
            <div>Scheduled minions: {{ scheduledMinions }}</div>
            <div>Started minions: {{ startedMinions }}</div>
            <div>Completed minions: {{ completedMinions }}</div>
        </template>
        <div class="flex items-center cursor-pointer" @click="handleRowClick">
            <BaseIcon icon="/icons/icon-two-users-grey.svg" />
            <apexchart
                height="24"
                width="96"
                :options="chartOptions"
                :series="minionBarChartDataSeries"
            />
            <span class="text-gray-500 pl-1">
                {{ completedMinions }} / {{ startedMinions }} / {{ scheduledMinions }}
            </span>
        </div>
    </a-tooltip>
    <a-modal
        v-if="minionsModalOpen"
        v-model:open="minionsModalOpen"
        :title="scenarioName"
        :footer="null"
        :closable="true">
        <section class="mt-5">
            <div class="flex items-center pb-2">
                <div :style="{ backgroundColor: scheduledMinionsColor}" class="color-legend mr-2"></div>
                <div>Scheduled minions: {{ scheduledMinions }}</div>
            </div>
            <div class="flex items-center pb-2">
                <div :style="{ backgroundColor: startedMinionsColor}" class="color-legend mr-2"></div>
                <div>Started minions: {{ startedMinions }}</div>
            </div>
            <div class="flex items-center">
                <div :style="{ backgroundColor: completedMinionsColor}" class="color-legend mr-2"></div>
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
    </a-modal>
</template>

<script setup lang="ts">
const props = defineProps<{
  /**
   * The number of scheduled minions.
   */
  scheduledMinions: number;

  /**
   * The number of started minions.
   */
  startedMinions: number;

  /**
   * The number of completed minions.
   */
  completedMinions: number;

  /**
   * The name of the scenario.
   */
  scenarioName: string;
}>();

/**
 * The color of the scheduled minions
 */
const scheduledMinionsColor = ColorsConfig.GREY_2_HEX_CODE;

/**
 * The color of the started minions.
 */
const startedMinionsColor = ColorsConfig.PURPLE_COLOR_HEX_CODE;

/**
 * The color of the completed minions.
 */
const completedMinionsColor = ColorsConfig.PRIMARY_COLOR_HEX_CODE;


const chartOptions = ScenarioDetailsConfig.MINION_STACKED_BAR_CHART_OPTIONS

const minionBarChartDataSeries = computed(() => ScenarioHelper.toMinionBarChartSeries(props.completedMinions, props.startedMinions, props.scheduledMinions))

const minionsModalOpen = ref(false)

const handleRowClick = () => {
    minionsModalOpen.value = true;
}

</script>
