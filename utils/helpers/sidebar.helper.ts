import { PermissionConstant } from "../types/permission";

enum PagePath {
    CAMPAIGNS = "campaigns",
    REPORTS = "reports",
    SERIES = "series"
}

export const pagePathToPermission: { [key: string]: PermissionEnum[] } = {
    [PagePath.CAMPAIGNS]: [PermissionConstant.READ_CAMPAIGN],
    [PagePath.REPORTS]: [PermissionConstant.READ_REPORT],
    [PagePath.SERIES]: [PermissionConstant.READ_SERIES]
};

export class SidebarHelper {
    static getMenuItems(): SidebarMenuItem[] {
        return [
            {
                id: "campaigns",
                icon: "/icons/icon-work-grey.svg",
                text: "Campaigns",
                path: "campaigns",
                permissions: pagePathToPermission[PagePath.CAMPAIGNS]
            },
            {
                id: "reports",
                icon: "/icons/icon-chart-light-grey.svg",
                text: "Reports",
                path: "reports",
                permissions: pagePathToPermission[PagePath.REPORTS]
            },
            {
                id: "series",
                icon: "/icons/icon-category-grey.svg",
                text: "Series",
                path: "series",
                permissions: pagePathToPermission[PagePath.SERIES]
            }
        ]
    }
}