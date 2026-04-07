import Fuse from "fuse.js"

export const SearchHelper = {
    performFuzzySearch<T>(query: string, list: T[], keys: string[]): T[] {
        if (!query) return list

        const options = { keys }
        const fuse = new Fuse(list, options)

        return fuse.search(query).map(res => res.item)
    },
}
