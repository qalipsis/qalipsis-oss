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
        v-for="meter in meters"
        :key="meter.name"
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
            <span class="text-gray-400 dark:text-gray-500">{{ formatPctLabel(k) }}:&nbsp;</span>
            <span class="text-gray-600 dark:text-gray-400"> {{ formatPctValue(meter, v) }}</span>
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

const headRowCls = computed(() =>
    props.variant === 'nested'
        ? 'bg-white dark:bg-primary-900'
        : 'bg-gray-50 dark:bg-primary-800',
)

/**
 * Format a duration as ms in a human-readable format.
 */
const _formatDuration = (d: string | number): string => {
  if (typeof d === 'number') return `${Math.round(d / 1_000_000)} ms`
  const m = /PT(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?/.exec(d)
  if (m) {
    const ms =
        (Number.parseInt(m[1] ?? '0') * 3_600_000) +
        (Number.parseInt(m[2] ?? '0') * 60_000) +
        (Number.parseFloat(m[3] ?? '0') * 1_000)
    return `${Math.round(ms)} ms`
  }
  return d
}

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
 * Filters the percentiles provided in the meter.
 */
const filteredPercentiles = (meter: TimeSeriesMeter): [string, number][] =>
    meter.other ? Object.entries(meter.other).filter(([k]) => k.startsWith('percentile_')) : []

/**
 * Format the label of the percentile based on the key.
 * For example, percentile_99.9 is returned as 99.9 and percentile_50.0 as 50.
 */
const formatPctLabel = (key: string): string =>
    'p' + key.replace('percentile_', '').replace(/\.0$/, '')

/**
 * Format the percentile value. If the meter is a timer, the value is converted from
 * microseconds to milliseconds and the unit is added.
 */
const formatPctValue = (meter: TimeSeriesMeter, value: number): string =>
    meter.type === 'timer' ? `${(value / 1000).toFixed(1)} ms` : String(Math.round(value))
</script>
