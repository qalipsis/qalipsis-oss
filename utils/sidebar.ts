import { PermissionEnum } from "./permission";

interface SidebarMenuBaseItem {
    /**
     * The text for the item.
     */
    text: string;
    /**
     * The url path
     */
    path: string;
    /**
     * Required permissions to view the page
     */
    permissions: PermissionEnum[];
}

export interface SidebarMenuItem extends SidebarMenuBaseItem {
    /**
     * Icon of the item
     */
    icon: string;
    /**
     * The sub menu items
     */
    subMenuItems?: SidebarMenuBaseItem[];
}

