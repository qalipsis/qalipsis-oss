import { PermissionConstant } from "../types/permission";

export class SidebarHelper {
    static getMenuItems(): SidebarMenuItem[] {
        return [
            {
                id: "campaigns",
                icon: "/icons/icon-work-grey.svg",
                text: "Campaigns",
                path: "campaigns",
                permissions: [PermissionConstant.READ_CAMPAIGN]
            },
            {
                id: "reports",
                icon: "/icons/icon-chart-light-grey.svg",
                text: "Reports",
                path: "reports",
                permissions: [PermissionConstant.READ_REPORT]
            },
            {
                id: "series",
                icon: "/icons/icon-category-grey.svg",
                text: "Series",
                path: "series",
                permissions: [PermissionConstant.READ_SERIES]
            }
        ]
    }
}