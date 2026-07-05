<template>
  <div class="flex flex-col gap-y-2 p-2 w-full">
    <div
        v-for="report in scenarioReports"
        :key="report.id"
        class="border border-gray-200 dark:border-gray-700 rounded-lg overflow-hidden bg-white dark:bg-primary-900"
    >
      <!-- ── SCENARIO HEADER ROW (accordion trigger) ── -->
      <div
          class="flex items-center gap-x-3 px-3 py-2 cursor-pointer select-none hover:bg-gray-50 dark:hover:bg-primary-800"
          @click="toggleScenario(report.id)"
      >
        <svg
            class="w-4 h-4 text-gray-400 flex-shrink-0 transition-transform duration-150"
            :class="{ 'rotate-90': expandedScenarios.has(report.id) }"
            viewBox="0 0 16 16" fill="none" stroke="currentColor"
            stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
        >
          <polyline points="6,4 10,8 6,12"/>
        </svg>

        <!-- Name -->
        <span
            class="font-medium text-gray-800 dark:text-gray-100 flex-shrink-0"
            :class="{
            'pl-4': report.id !== ScenarioDetailsConfig.SCENARIO_SUMMARY_ID && scenarioReports.length > 1,
          }"
        >{{ report.name }}</span>

        <ScenarioTag
            :status="report.status"
            :failureReason="report.failureReason"
            @click.stop="handleTagClick(report)"
        />

        <div class="w-px h-6 bg-gray-200 dark:bg-gray-600 flex-shrink-0 mx-1"></div>

        <!-- Time -->
        <template v-if="report.start">
          <ScenarioElapsedTime :start="report.start" :end="report.end"/>
        </template>

        <div class="flex-1"></div>

        <template v-if="!isRunning">
          <!-- Minions -->
          <ScenarioMinions
              :status="status"
              :scenario-name="report.name"
              :scheduled-minions="report.scheduledMinions"
              :started-minions="report.startedMinions ?? 0"
              :completed-minions="report.completedMinions ?? 0"
          />

          <div class="w-px h-6 bg-gray-200 dark:bg-gray-600 flex-shrink-0 mx-1"></div>

          <!-- Step executions -->
          <ScenarioExecutionSteps
              :status="status"
              :showLabels="true"
              :scenario-name="report.name"
              :successful-executions="report.successfulExecutions ?? 0"
              :failed-executions="report.failedExecutions ?? 0"
          />

          <div class="w-px h-6 bg-gray-200 dark:bg-gray-600 flex-shrink-0 mx-1"></div>

          <!-- Failure rate -->
          <div class="flex flex-col items-center text-sm flex-shrink-0 min-w-[52px]">
            <span class="font-semibold" :class="failRateCls(report)">{{ failRateText(report) }}</span>
            <span class="text-xs text-gray-400">Failure</span>
          </div>
        </template>
      </div>

      <!-- ── SCENARIO BODY ── -->
      <div
          v-if="expandedScenarios.has(report.id)"
          class="border-t border-gray-100 dark:border-gray-700 px-4 py-4 flex flex-col gap-y-4"
      >
        <!-- Running campaign placeholder: meters and steps not available yet -->
        <template v-if="isRunning">
          <div class="flex items-center justify-center py-8 gap-x-2 text-gray-400 dark:text-gray-500">
            <svg class="w-4 h-4 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
            </svg>
            <span class="text-sm">Metrics and step details will be available once the campaign completes.</span>
          </div>
        </template>

        <template v-else>
          <!-- Zone distribution -->
          <div
              v-if="report.zoneDistribution && Object.keys(report.zoneDistribution).length > 0"
              class="flex items-center gap-x-2 flex-wrap"
          >
            <span class="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 flex-shrink-0">
              Zone distribution
            </span>
            <span
                v-for="(percentage, zoneKey) in report.zoneDistribution"
                :key="zoneKey"
                class="inline-flex items-center gap-x-1.5 px-2 py-0.5 text-xs font-semibold text-gray-800 dark:text-gray-200"
            >
              <img
                  v-if="zones[zoneKey]?.imagePath"
                  :src="zones[zoneKey]!.imagePath"
                  :alt="zones[zoneKey]!.title"
                  class="w-3.5 h-3.5 rounded-full object-cover flex-shrink-0"
              />
              <span>{{ zones[zoneKey]?.title ?? zoneKey }}</span>
              <span class="text-gray-800 dark:text-gray-200 font-mono">{{ percentage }}%</span>
            </span>
          </div>

          <!-- Scenario-level messages -->
          <div v-if="report.messages?.length > 0">
            <div class="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-2">
              Scenario Messages
            </div>
            <div class="flex flex-col gap-y-1">
              <div
                  v-for="msg in report.messages"
                  :key="msg.messageId"
                  class="flex items-start gap-x-2 px-3 py-2 rounded"
                  :class="msgBgCls(msg.severity)"
              >
                <span class="text-xs font-bold flex-shrink-0 w-12" :class="msgTextCls(msg.severity)">{{
                    msg.severity
                  }}</span>
                <span class="text-sm text-gray-700 dark:text-gray-300">{{ msg.message }}</span>
              </div>
            </div>
          </div>

          <!-- Scenario-level meters table -->
          <div v-if="report.meters?.length > 0">
            <div class="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400 mb-2">
              Meters
            </div>
            <ScenarioMeterTable :meters="report.meters!"/>
          </div>

          <!-- Steps -->
          <div v-if="report.steps?.length > 0">
            <div class="flex items-center mb-2">
              <span class="text-xs font-semibold uppercase tracking-wider text-gray-500 dark:text-gray-400">Steps</span>
            </div>
            <div class="flex flex-col gap-y-1">
              <div
                  v-for="(step, stepIndex) in report.steps ?? []"
                  :key="stepIndex"
                  class="border rounded"
                  :class="stepBorderCls(step)"
              >
                <!-- Step row -->
                <div
                    class="flex items-center gap-x-2 px-3 py-1.5"
                    :class="{ 'cursor-pointer': !isStepNotExecuted(step) && stepHasDetail(step) }"
                    @click="!isStepNotExecuted(step) && stepHasDetail(step) && toggleStep(report.id, stepIndex)"
                >
                  <span class="font-mono text-sm text-gray-800 dark:text-gray-200 flex-1 truncate">{{
                      step.name
                    }}</span>
                  <template v-if="!isStepNotExecuted(step)">
                    <ScenarioExecutionSteps
                        :status="step.status"
                        :scenario-name="step.name"
                        :successful-executions="step.successfulExecutions ?? 0"
                        :failed-executions="step.failedExecutions ?? 0"
                        :show-labels="false"
                    />
                    <div class="w-px h-6 bg-gray-200 dark:bg-gray-600 flex-shrink-0"></div>
                    <span class="font-semibold text-sm flex-shrink-0"
                          :class="stepFailRateCls(step)">{{ stepFailRateText(step) }}</span>
                  </template>
                  <span
                      v-if="isStepNotExecuted(step)"
                      class="flex-shrink-0 px-2 py-0.5 rounded text-xs font-medium bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400"
                  >N/A, Off-load</span>
                  <ScenarioTag v-else :status="step.status" class="flex-shrink-0"/>
                  <button
                      v-if="!isStepNotExecuted(step) && stepHasDetail(step)"
                      class="text-xs px-2 py-0.5 rounded border border-gray-200 dark:border-gray-600 text-gray-500 hover:bg-gray-50 dark:hover:bg-primary-800 flex-shrink-0"
                      @click.stop="toggleStep(report.id, stepIndex)"
                  >
                    Details <span class="text-gray-400">{{
                      expandedSteps.has(`${report.id}:${stepIndex}`) ? '▴' : '▾'
                    }}</span>
                  </button>
                </div>

                <!-- Step detail panel -->
                <div
                    v-if="!isStepNotExecuted(step) && stepHasDetail(step) && expandedSteps.has(`${report.id}:${stepIndex}`)"
                    class="border-t border-gray-100 dark:border-gray-700 px-4 py-3 flex flex-col gap-y-3 bg-gray-50 dark:bg-primary-950"
                >
                  <!-- Step messages -->
                  <div v-if="step.messages?.length > 0">
                    <div class="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500 mb-1">
                      Messages
                    </div>
                    <div class="flex flex-col gap-y-1">
                      <div
                          v-for="msg in step.messages"
                          :key="msg.messageId"
                          class="flex items-start gap-x-2 px-3 py-1.5 rounded"
                          :class="msgBgCls(msg.severity)"
                      >
                        <span class="text-xs font-bold flex-shrink-0 w-12"
                              :class="msgTextCls(msg.severity)">{{ msg.severity }}</span>
                        <span class="text-sm text-gray-700 dark:text-gray-300">{{ msg.message }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- Step meters table -->
                  <div v-if="step.meters?.length > 0">
                    <div class="text-xs font-semibold uppercase tracking-wider text-gray-400 dark:text-gray-500 mb-1">
                      Meters
                    </div>
                    <ScenarioMeterTable :meters="step.meters!" variant="nested"/>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>

  <ScenarioMessageDrawer
    v-if="scenarioDrawer.open"
    v-model:open="scenarioDrawer.open"
    :title="scenarioDrawer.title"
    :messages="scenarioDrawer.messages"
  />
</template>

<script setup lang="ts">
const props = defineProps<{
  status: ExecutionStatus
  scenarioReports: ScenarioReport[]
  zones: { [key: string]: Zone }
}>()

const isRunning = computed(() =>
    props.status === ExecutionStatusConstant.IN_PROGRESS ||
    props.status === ExecutionStatusConstant.QUEUED ||
    props.status === ExecutionStatusConstant.SCHEDULED,
)

// ── Accordion state ──────────────────────────────────────────────────────────

const expandedScenarios = ref(new Set<string>())
const expandedSteps = ref(new Set<string>())

const toggleScenario = (id: string) => {
  const s = expandedScenarios.value
  if (s.has(id)) s.delete(id)
  else s.add(id)
}

const toggleStep = (scenarioId: string, stepIndex: number) => {
  const key = `${scenarioId}:${stepIndex}`
  const s = expandedSteps.value
  if (s.has(key)) s.delete(key)
  else s.add(key)
}

const stepHasDetail = (step: StepExecutionDetails): boolean =>
    (step.messages?.length ?? 0) > 0 || (step.meters?.length ?? 0) > 0

const isStepNotExecuted = (step: StepExecutionDetails): boolean =>
    step.notExecuted || ((step.successfulExecutions ?? 0) + (step.failedExecutions ?? 0)) === 0

// ── Failure-rate helpers ─────────────────────────────────────────────────────

const _failRate = (success: number, failed: number): number | null => {
  const total = success + failed
  return total > 0 ? (failed * 100) / total : null
}

const failRateText = (report: ScenarioReport): string => {
  const rate = _failRate(report.successfulExecutions ?? 0, report.failedExecutions ?? 0)
  return rate !== null ? `${rate.toFixed(1)}%` : '0.0%'
}

const failRateCls = (report: ScenarioReport): string => {
  const rate = _failRate(report.successfulExecutions ?? 0, report.failedExecutions ?? 0)
  if (rate === null || rate === 0) return 'text-gray-400'
  return rate > 10 ? 'text-red-600 dark:text-red-400' : 'text-orange-500 dark:text-orange-400'
}

const stepFailRateText = (step: StepExecutionDetails): string => {
  const rate = _failRate(step.successfulExecutions ?? 0, step.failedExecutions ?? 0)
  return rate !== null ? `${rate.toFixed(1)}%` : '0.0%'
}

const stepFailRateCls = (step: StepExecutionDetails): string => {
  const rate = _failRate(step.successfulExecutions ?? 0, step.failedExecutions ?? 0)
  if (rate === null || rate === 0) return 'text-gray-400'
  return rate > 10 ? 'text-red-600 dark:text-red-400' : 'text-orange-500 dark:text-orange-400'
}

const stepBorderCls = (step: StepExecutionDetails): string => {
  if (isStepNotExecuted(step)) return 'border-gray-200 dark:border-gray-600 opacity-60'
  const s = step.status
  if (s === ExecutionStatusConstant.SUCCESSFUL) return 'border-green-300 dark:border-green-700'
  if (s === ExecutionStatusConstant.WARNING) return 'border-yellow-300 dark:border-yellow-600'
  if (s === ExecutionStatusConstant.FAILED || s === ExecutionStatusConstant.ABORTED)
    return 'border-red-300 dark:border-red-700'
  return 'border-gray-200 dark:border-gray-600'
}

// ── Message severity styles ──────────────────────────────────────────────────

const msgBgCls = (severity: ReportMessageSeverity): string => {
  if (severity === ReportMessageSeverityConstant.INFO) return 'bg-blue-50 dark:bg-blue-900/30'
  if (severity === ReportMessageSeverityConstant.WARN) return 'bg-yellow-50 dark:bg-yellow-900/30'
  return 'bg-red-50 dark:bg-red-900/30'
}

const msgTextCls = (severity: ReportMessageSeverity): string => {
  if (severity === ReportMessageSeverityConstant.INFO) return 'text-blue-600 dark:text-blue-400'
  if (severity === ReportMessageSeverityConstant.WARN) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
}

// ── Scenario message drawer (keep existing behaviour) ────────────────────────

const scenarioDrawer = reactive<ScenarioDrawer>({
  open: false,
  messages: [],
  title: '',
})

const handleTagClick = (report: ScenarioReport) => {
  scenarioDrawer.open = true
  scenarioDrawer.title = report.name
  scenarioDrawer.messages = (report.messages ?? []).map((message) => ({
    ...message,
    severityTag: ScenarioHelper.toSeverityTag(message.severity),
  }))
}
</script>
