export const useUserApi = () => {
    const { get } = useApi();

    const fetchProfile = (): Promise<Profile> => {
        return get<Profile>("/users/profile");
    }

    /**
     * Fetches the available permissions for the user.
     * 
     * @returns The available permissions
     */
    const fetchPermissions = (): Promise<PermissionEnum[]> => {
        return get<PermissionEnum[]>('/users/permissions')
    }

    return {
        fetchProfile: fetchProfile,
        fetchPermissions: fetchPermissions
    }
}