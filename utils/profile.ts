export interface Profile {
    /**
     * Details of the user
     */
    user: User;

    /**
     * Tenants accessible to the user
     */
    tenants: Tenant[];
}