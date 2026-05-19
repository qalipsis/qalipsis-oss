interface UserStoreState {
  user: User | null
  tenants: Tenant[]
  currentTenantReference: string
  permissions: string[]
}

export const useUserStore = defineStore('User', {
  state: (): UserStoreState => {
    return {
      user: null,
      currentTenantReference: localStorage.getItem(TenantConfig.TENANT_LOCAL_STORAGE_PROPERTY_KEY) ?? '',
      tenants: [],
      permissions: [],
    }
  },
  getters: {
    currentTenant: (state) => state.tenants.find((tenant: Tenant) => tenant.reference === state.currentTenantReference),
  },
  actions: {
    storeTenantToLocalStorage(tenantReference: string): void {
      localStorage.setItem(TenantConfig.TENANT_LOCAL_STORAGE_PROPERTY_KEY, tenantReference)
    },
    hasAnyPermission(requiredPermissions: string[]): boolean {
      return requiredPermissions.length === 0
        ? true
        : requiredPermissions.some((requiredPermission) => this.permissions.includes(requiredPermission))
    },
    hasAllPermission(requiredPermissions: string[]): boolean {
      return requiredPermissions.length === 0
        ? true
        : requiredPermissions.every((requiredPermission) => this.permissions.includes(requiredPermission))
    },
  },
})
