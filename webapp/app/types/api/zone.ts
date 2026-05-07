export interface Zone {
    /**
     * A more detailed definition of the zone, generally the region, datacenter and the localization details
     */
    description: string;
    /**
     * A unique identifier for the zone
     */
    key: string;
    /**
     * A complete name of the zone, generally the country
     */
    title: string;
    /**
     * Image URL to display for the zone
     */
    imagePath: string;
    /**
     * A flag to indicate if the zone is enabled.
     */
    enabled: boolean;
}
