import Fuse from "fuse.js";

export class SearchHelper {
    static getSanitizedQuery = (query: string): string => {
        /**
         * Steps for sanitizing the query
         * 1. trims the spaces
         * 2. removes the double quotation
         * 3. replaces the white spaces to comma
         * 4. splits the query by comma
         * 5. trims the space of each value in the query
         * 6. filters the empty value
         * 7. join the text by comma
         */
        return query
            .trim()
            .replace(/"/g, '')
            .replace(/ /g, ',')
            .split(',')
            .map(text => text.trim())
            .filter(text => text)
            .join(',')
    }

    static performFuzzySearch<T>(query: string, list: T[], keys: string[]): T[] {
        if (!query) return list;

        const options = {
            keys
        };
        // Use fuse library to perform the fuzzy search.
        const fuse = new Fuse(list, options);

        return fuse.search(query).map(res => res.item)
    }
}