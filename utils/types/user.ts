export interface User {
    /**
     * Tenant owning the user
     */
    tenant: string;

    /**
     * Unique identifier of the user
     */
    username: string;

    /**
     * A flag to indicate if the user is blocked
     */
    blocked: boolean;

    /**
     * The creation timestamp of the user in ISO string format
     */
    creation: string;

    /**
     * The display name of the user
     */
    displayName: string;

    /**
     * The email of the user
     */
    email: string;

    /**
     * A flag to indicate if the email has been verified
     */
    emailVerified: boolean;

    /**
     * The family name of the user
     */
    familyName: string;

    /**
     * The given name of the user
     */
    givenName: string;

    /**
     * The available roles from the current tenant.
     */
    roles: string[];

    /**
     * The status
     */
    status: 'ACTIVE' | 'INACTIVE';

    /**
     * The latest timestamp when the user updates the data in ISO string format
     */
    version: string;
}