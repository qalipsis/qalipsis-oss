import { PermissionConstant } from "../types/permission";

export class SidebarHelper {
    static getMenuItems(): SidebarMenuItem[] {
        return [
            {
                id: "campaigns",
                icon: "qls-icon-work",
                text: "Campaigns",
                path: "campaigns",
                permissions: [PermissionConstant.READ_CAMPAIGN]
            },
            {
                id: "reports",
                icon: "qls-icon-chart-stroke",
                text: "Reports",
                path: "reports",
                permissions: [PermissionConstant.READ_REPORT]
            },
            {
                id: "series",
                icon: "qls-icon-category",
                text: "Series",
                path: "series",
                permissions: [PermissionConstant.READ_SERIES]
            }
        ]
    }
}