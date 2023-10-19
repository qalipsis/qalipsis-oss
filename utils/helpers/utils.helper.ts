export function debounce<F extends (...args: Parameters<F>) => ReturnType<F>>(
    func: F,
    waitFor: number,
): (...args: Parameters<F>) => void {
    let timeout: NodeJS.Timeout;
    
    return (...args: Parameters<F>): void => {
        clearTimeout(timeout);
        timeout = setTimeout(() => func(...args), waitFor);
    };
}

export function objectsEqual<T>(o1: T, o2: T): boolean {
    return typeof o1 === 'object' && Object.keys(o1 as Object).length > 0 
        ? Object.keys(o1 as Object).length === Object.keys(o2 as Object).length && Object.keys(o1 as Object)
            .every(p => objectsEqual(o1?.[p as keyof typeof o1], o2?.[p as keyof typeof o2]))
        : o1 === o2;
}
    
export function arraysEqual<T>(a1: T[], a2: T[]): boolean {
    return a1.length === a2.length && a1.every((o, idx) => objectsEqual<T>(o, a2[idx]));
} 