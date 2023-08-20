import { defineStore } from "pinia";

interface UserStoreState {
  user?: User;
  tenants: Tenant[];
  currentTenantReference: string;
  permissions: PermissionEnum[];
}

export const useUserStore = defineStore("User", {
  state: (): UserStoreState => {
      return {
          currentTenantReference: localStorage.getItem(TenantHelper.TENANT_LOCAL_STORAGE_PROPERTY_KEY) ?? '',
          tenants: [],
          permissions: []
      }
  },
  getters: {
      currentTenant: state => state.tenants.find(tenant => tenant.reference === state.currentTenantReference)
  },
  actions: {
      storeTenant(tenantReference: string): void {
          this.currentTenantReference = tenantReference;
          localStorage.setItem(TenantHelper.TENANT_LOCAL_STORAGE_PROPERTY_KEY, tenantReference);
      },
      hasAnyPermission(requiredPermissions: PermissionEnum[]): boolean {
          console.log(requiredPermissions)
          console.log(this.permissions)
          console.log(requiredPermissions.some(requiredPermission => this.permissions.includes(requiredPermission)))
          return requiredPermissions.length === 0 ?
              true : requiredPermissions.some(requiredPermission => this.permissions.includes(requiredPermission))
      },
      hasAllPermission(requiredPermissions: PermissionEnum[]): boolean {
          return requiredPermissions.length === 0 ?
              true : requiredPermissions.every(requiredPermission => this.permissions.includes(requiredPermission))
      }
  }
});
