export const useUserApi = () => {
    const { get$ } = baseApi();

    /**
     * Fetches the profile of the user
     * 
     * @returns The profile of the user
     */
    const fetchProfile = (): Promise<Profile> => {
        return get$<Profile>("/users/profile");
    }

    /**
     * Fetches the available permissions for the user.
     * 
     * @returns The available permissions
     */
    const fetchPermissions = (): Promise<PermissionEnum[]> => {
        return get$<PermissionEnum[]>('/users/permissions')
    }

    return {
        fetchProfile,
        fetchPermissions
    }
}