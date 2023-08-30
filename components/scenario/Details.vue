<template>
    <table class="scenario-details-table">
      <tr v-for="report in scenarioReports" :key="report.id" >
        <td>
            <span class="text-grey-1">{{ report.name }}</span>
        </td>
        <td>
          <ScenarioTag
            :status="report.status"
            :failureReason="report.failureReason"
            @click="handleTagClick(report)"/>
        </td>
        <td>
          <ScenarioElapsedTime :start="report.start" :end="report.end" />
        </td>
        <td>
          <ScenarioMinions
            :scenario-name="report.name"
            :scheduled-minions="report.scheduledMinions"
            :completed-minions="report.completedMinions"
            :started-minions="report.startedMinions"
          />
        </td>
        <td>
          <ScenarioExecutionSteps
            :scenario-name="report.name"
            :successful-executions="report.successfulExecutions"
            :failed-executions="report.failedExecutions"
          />
        </td>
      </tr>
    </table>
</template>

<script setup lang="ts">
defineProps<{
    scenarioReports: ScenarioReport[]
}>();

const handleTagClick = (report: ScenarioReport) => {

}

</script>

<style lang="scss" scoped>
.scenario-details-table {
    tr {
        height: 1.75rem;
    }
}
</style>