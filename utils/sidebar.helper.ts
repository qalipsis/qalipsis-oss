enum PagePath {
    CAMPAIGNS = "campaigns",
    REPORTS = "reports",
    SERIES = "series"
}

export const pagePathToPermission = {
    [PagePath.CAMPAIGNS]: [PermissionEnum.READ_CAMPAIGN],
    [PagePath.REPORTS]: [PermissionEnum.READ_REPORT],
    [PagePath.SERIES]: [PermissionEnum.READ_SERIES]
};

export class SidebarHelper {
    static getMenuItems() {
        return [
            {
                icon: "/icons/icon-work-grey.svg",
                text: "Campaigns",
                path: "campaigns",
                permissions: pagePathToPermission[PagePath.CAMPAIGNS]
            },
            {
                icon: "/icons/icon-chart-light-grey.svg",
                text: "Reports",
                path: "reports",
                permissions: pagePathToPermission[PagePath.REPORTS]
            },
            {
                icon: "/icons/icon-category-grey.svg",
                text: "Series",
                path: "series",
                permissions: pagePathToPermission[PagePath.SERIES]
            }
        ]
    }
}