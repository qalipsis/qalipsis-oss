import tinycolor from "tinycolor2"

export const ColorHelper = {
    /**
     * Checks if the hex code is valid
     *
     * @param hexCode The hex code
     *
     * @returns a flag to indicates if the hex code is valid
     */
    isValidHexCode(hexCode: string): boolean {
        return tinycolor(hexCode).isValid()
    },

    /**
     * Enriches the hex code with opacity
     *
     * @param hexCode The 6 digit hex code. E.g, #41c9ca
     * @param opacity The opacity (range from 1 - 100)
     *
     * @returns The hex code with opacity E.g., #41c9caa2
     */
    enrichHexCodeWithOpacity(hexCode?: string, opacity?: number): string {
        if (!hexCode) return ColorsConfig.PRIMARY_COLOR_HEX_CODE

        try {
            const color = tinycolor(hexCode)

            if (opacity !== undefined) {
                color.setAlpha(opacity / 100)
            }

            return `#${color.toHex8()}`
        } catch (error) {
            console.error(`Cannot parse hex code ${hexCode} and opacity ${opacity}`)
            throw error
        }
    },

    getHexCode(enrichedHexCode: string): string {
        return `#${tinycolor(enrichedHexCode).toHex().toUpperCase()}`
    },

    getOpacity(enrichedHexCode: string): number {
        const opacity = tinycolor(enrichedHexCode).getAlpha() * 100

        return Math.round(opacity)
    },
}
