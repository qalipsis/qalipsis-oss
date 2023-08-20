import { PermissionEnum } from "./permission";

export class SidebarHelper {
    static getMenuItems() {
        return [
            {
                icon: '/icons/icon-work-grey.svg',
                text: 'Campaigns',
                path: 'campaigns',
                permissions: [PermissionEnum.READ_CAMPAIGN]
            },
            {
                icon: '/icons/icon-chart-light-grey.svg',
                text: 'Reports',
                path: 'reports',
                permissions: [PermissionEnum.READ_REPORT]
            },
            {
                icon: '/icons/icon-category-grey.svg',
                text: 'Series',
                path: 'series',
                permissions: [PermissionEnum.READ_SERIES]
            }
        ]
    }
}