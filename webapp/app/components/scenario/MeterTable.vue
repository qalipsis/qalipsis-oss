<template>
  <table class="w-full text-sm border-collapse table-fixed">
    <colgroup>
      <col class="w-[32%]"/>
      <col class="w-[12%]"/>
      <col class="w-[10%]"/>
      <col class="w-[15%]"/>
      <col class="w-[13%]"/>
      <col class="w-[18%]"/>
    </colgroup>
    <thead>
    <tr :class="headRowCls">
      <th class="text-left px-2 py-1.5 text-gray-600 dark:text-gray-300 font-medium text-xs">Name</th>
      <th class="text-left px-2 py-1.5 text-gray-600 dark:text-gray-300 font-medium text-xs">Type</th>
      <th class="text-right px-2 py-1.5 text-gray-600 dark:text-gray-300 font-medium text-xs">Count</th>
      <th class="text-right px-2 py-1.5 text-gray-600 dark:text-gray-300 font-medium text-xs">Value / Mean</th>
      <th class="text-right px-2 py-1.5 text-gray-600 dark:text-gray-300 font-medium text-xs">Max</th>
      <th class="text-right px-2 py-1.5 text-gray-600 dark:text-gray-300 font-medium text-xs">Percentiles</th>
    </tr>
    </thead>
    <tbody>
    <tr
        v-for="meter in displayedMeters"
        :key="meterKey(meter)"
        class="border-t border-gray-100 dark:border-gray-700 align-top"
    >
      <td class="px-2 py-1.5">
        <span class="text-gray-800 dark:text-gray-200 font-mono text-xs">{{ meter.name }}</span>
        <div v-if="filteredTags(meter).length > 0" class="mt-0.5">
          <div
              v-for="[k, v] in filteredTags(meter)"
              :key="k"
              class="font-mono text-xs text-gray-400 dark:text-gray-500"
          >{{ k }}={{ v }}
          </div>
        </div>
      </td>
      <td class="px-2 py-1.5">
          <span
              class="px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 text-xs font-mono">
            {{ meter.type ?? '—' }}
          </span>
      </td>
      <td class="px-2 py-1.5 text-right text-gray-600 dark:text-gray-400">{{ meter.count ?? '—' }}</td>
      <td class="px-2 py-1.5 text-right text-gray-600 dark:text-gray-400">{{ meterValueOrMean(meter) }}</td>
      <td class="px-2 py-1.5 text-right text-gray-600 dark:text-gray-400">{{ meterMax(meter) }}</td>
      <td class="px-2 py-1.5 text-right">
        <template v-if="filteredPercentiles(meter).length > 0">
          <div v-for="[k, v] in filteredPercentiles(meter)" :key="k" class="font-mono text-xs leading-tight">
            <span class="text-gray-400 dark:text-gray-500">{{ k }}:&nbsp;</span>
            <span class="text-gray-600 dark:text-gray-400"> {{ v }}</span>
          </div>
        </template>
        <span v-else class="text-gray-600 dark:text-gray-400">—</span>
      </td>
    </tr>
    </tbody>
  </table>
</template>

<script setup lang="ts">
const props = defineProps<{
  meters: TimeSeriesMeter[]
  variant?: 'default' | 'nested'
}>()

/**
 * List of meter tags, for which the values should not be displayed.
 */
const EXCLUDED_TAG_KEYS = new Set(['scope', 'step', 'dag', 'previous-step'])

const filteredTags = (meter: TimeSeriesMeter): [string, string][] =>
    meter.tags ? Object.entries(meter.tags).filter(([k]) => !EXCLUDED_TAG_KEYS.has(k)) : []

/**
 * Identity of a meter, made of its name and all its tags. The REST API returns one row per factory
 * and per publication, hence several rows sharing the very same identity.
 */
const meterKey = (meter: TimeSeriesMeter): string => {
  const tags = Object.entries(meter.tags ?? {})
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, value]) => `${key}=${value}`)
      .join(',')

  return `${meter.name}|${tags}`
}

const _timestampOf = (meter: TimeSeriesMeter): number => Date.parse(meter.timestamp) || 0

/**
 * Only the latest snapshot of each distinct meter identity, in order of first appearance.
 */
const displayedMeters = computed(() => {
  const latestByIdentity = new Map<string, TimeSeriesMeter>()
  props.meters.forEach((meter) => {
    const key = meterKey(meter)
    const alreadyKept = latestByIdentity.get(key)
    if (!alreadyKept || _timestampOf(meter) >= _timestampOf(alreadyKept)) {
      latestByIdentity.set(key, meter)
    }
  })

  return [...latestByIdentity.values()]
})

const headRowCls = computed(() =>
    props.variant === 'nested'
        ? 'bg-white dark:bg-primary-900'
        : 'bg-gray-50 dark:bg-primary-800',
)

/**
 * Format an ISO-8601 duration as a human-readable millisecond value.
 */
const _formatDuration = (d: string): string => DurationFormatHelper.formatIso(d)

const meterValueOrMean = (meter: TimeSeriesMeter): string => {
  if (meter.mean != null) return String(meter.mean)
  if (meter.meanDuration != null) return _formatDuration(meter.meanDuration)
  if (meter.value != null) return String(meter.value)
  return '—'
}

const meterMax = (meter: TimeSeriesMeter): string => {
  if (meter.max != null) return String(meter.max)
  if (meter.maxDuration != null) return _formatDuration(meter.maxDuration)
  return '—'
}

/**
 * Prefix of the percentile keys in the additional values of the meters.
 */
const PERCENTILE_PREFIX = 'percentile_'

/**
 * Returns the percentiles of the meter as label and formatted value: durations for the timers,
 * plain values for the other meters.
 */
const filteredPercentiles = (meter: TimeSeriesMeter): [string, string][] => {
  if (meter.percentileDurations) {
    return Object.entries(meter.percentileDurations)
        .map(([percentage, duration]): [string, string] => [formatPctLabel(percentage), _formatDuration(duration)])
  }

  return Object.entries(meter.other ?? {})
      .filter(([key]) => key.startsWith(PERCENTILE_PREFIX))
      .map(([key, value]): [string, string] => [formatPctLabel(key.replace(PERCENTILE_PREFIX, '')), String(value)])
}

/**
 * Format the label of the percentile based on the percentage.
 * For example, 99.9 is returned as p99.9 and 50.0 as p50.
 */
const formatPctLabel = (percentage: string): string => 'p' + percentage.replace(/\.0$/, '')
</script>
