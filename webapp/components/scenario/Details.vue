<template>
  <table class="scenario-details-table">
    <tr v-for="report in scenarioReports" :key="report.id">
      <td>
        <span class="text-gray-500 dark:text-gray-100">{{ report.name }}</span>
      </td>
      <td>
        <ScenarioTag
            :status="report.status"
            :failureReason="report.failureReason"
            @click="handleTagClick(report)"/>
      </td>
      <td>
        <ScenarioElapsedTime :start="report.start!" :end="report.end"/>
      </td>
      <td>
        <ScenarioMinions
            :scenario-name="report.name"
            :scheduled-minions="report.scheduledMinions"
            :completed-minions="report.completedMinions!"
            :started-minions="report.startedMinions!"
        />
      </td>
      <td>
        <ScenarioExecutionSteps
            :scenario-name="report.name"
            :successful-executions="report.successfulExecutions!"
            :failed-executions="report.failedExecutions!"
        />
      </td>
    </tr>
  </table>
  <ScenarioMessageDrawer
      v-if="scenarioDrawer.open"
      v-model:open="scenarioDrawer.open"
      :title="scenarioDrawer.title"
      :messages="scenarioDrawer.messages"
  >
  </ScenarioMessageDrawer>
</template>

<script setup lang="ts">

defineProps<{
  scenarioReports: ScenarioReport[]
}>();


/**
 * The object includes the properties for the scenario drawer.
 *
 * @see ScenarioDrawer
 */
const scenarioDrawer = reactive<ScenarioDrawer>({
  open: false,
  messages: [],
  title: ''
})


const handleTagClick = (report: ScenarioReport) => {
  scenarioDrawer.open = true;
  scenarioDrawer.title = report.name;
  scenarioDrawer.messages = report.messages.map(message => ({
    ...message,
    severityTag: ScenarioHelper.toSeverityTag(message.severity)
  }))
}

</script>

<style lang="scss" scoped>
.scenario-details-table {
  tr {
    height: 1.75rem;
  }
}
</style>