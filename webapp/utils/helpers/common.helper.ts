export function debounce<F extends (...args: Parameters<F>) => ReturnType<F>>(
  func: F,
  waitFor: number
): (...args: Parameters<F>) => void {
  let timeout: NodeJS.Timeout

  return (...args: Parameters<F>): void => {
    clearTimeout(timeout)
    timeout = setTimeout(() => func(...args), waitFor)
  }
}

// Deep equality check for objects and arrays.
export function objectsEqual<T>(o1: T, o2: T): boolean {
  // Case 1: Strict equality covers.
  // - same reference for objects.
  // - primitive values that are equal.
  if (o1 === o2) return true

  // If either is null but not both → not equal.
  if (o1 === null || o2 === null) return false

  // If either is not an object (but they are not strictly equal) → not equal.
  // Covers cases like number vs string, function, etc.
  if (typeof o1 !== 'object' || typeof o2 !== 'object') return false

  // Case 2: Handle arrays.
  if (Array.isArray(o1) && Array.isArray(o2)) {
    // Delegates to a helper function arraysEqual (not shown here).
    return arraysEqual(o1, o2)
  }

  // If one is array and the other is not → not equal.
  if (Array.isArray(o1) || Array.isArray(o2)) return false

  // Case 3: Handle plain objects.
  const keys1 = Object.keys(o1 as object)
  const keys2 = Object.keys(o2 as object)

  // If objects have different number of keys → not equal.
  if (keys1.length !== keys2.length) return false

  // Recursively compare all properties.
  return keys1.every((key) => objectsEqual((o1 as Record<string, unknown>)[key], (o2 as Record<string, unknown>)[key]))
}

export function arraysEqual<T>(a1: T[], a2: T[]): boolean {
  if (a1 === a2) return true
  if (a1.length !== a2.length) return false

  return a1.every((item, idx) => objectsEqual(item, a2[idx]))
}
