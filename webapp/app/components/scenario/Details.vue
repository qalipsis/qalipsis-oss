<template>
  <div class="flex flex-col gap-y-4 p-2">
    <div
      v-for="report in scenarioReports"
      :key="report.id"
      class="grid md:grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-x-4 gap-y-2"
    >
      <div class="md:col-span-1 lg:col-span-2 xl:col-span-1 flex items-center justify-between gap-x-2">
        <span
          class="text-gray-700 dark:text-gray-100"
          :class="{
            'pl-4': report.id !== ScenarioDetailsConfig.SCENARIO_SUMMARY_ID && scenarioReports.length > 1,
          }"
          >{{ report.name }}</span
        >
        <ScenarioTag
          :status="report.status"
          :failureReason="report.failureReason"
          @click="handleTagClick(report)"
        />
      </div>
      <div
        class="md:col-span-1 lg:col-span-2 xl:col-span-2 flex flex-col lg:flex-row lg:items-center w-full gap-x-4 gap-y-4"
      >
        <div :class="elapsedTimeSectionWidth">
          <ScenarioElapsedTime
            :start="report.start!"
            :end="report.end"
          />
        </div>
        <div class="w-60">
          <ScenarioMinions
            :status="status"
            :scenario-name="report.name"
            :scheduled-minions="report.scheduledMinions"
            :completed-minions="report.completedMinions!"
            :started-minions="report.startedMinions!"
          />
        </div>
        <div class="w-48">
          <ScenarioExecutionSteps
            :status="status"
            :scenario-name="report.name"
            :successful-executions="report.successfulExecutions!"
            :failed-executions="report.failedExecutions!"
          />
        </div>
      </div>
    </div>
  </div>

  <ScenarioMessageDrawer
    v-if="scenarioDrawer.open"
    v-model:open="scenarioDrawer.open"
    :title="scenarioDrawer.title"
    :messages="scenarioDrawer.messages"
  >
  </ScenarioMessageDrawer>
</template>

<script setup lang="ts">
const props = defineProps<{
  status: ExecutionStatus
  scenarioReports: ScenarioReport[]
}>()

/**
 * The object includes the properties for the scenario drawer.
 *
 * @see ScenarioDrawer
 */
const scenarioDrawer = reactive<ScenarioDrawer>({
  open: false,
  messages: [],
  title: '',
})

const elapsedTimeSectionWidth = computed(() =>
  ScenarioDetailsHelper.getElapsedTimeSectionWidthClass(props.scenarioReports),
)

const handleTagClick = (report: ScenarioReport) => {
  scenarioDrawer.open = true
  scenarioDrawer.title = report.name
  scenarioDrawer.messages = report.messages.map((message) => ({
    ...message,
    severityTag: ScenarioHelper.toSeverityTag(message.severity),
  }))
}
</script>
