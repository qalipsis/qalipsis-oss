interface SidebarMenuBaseItem {
    /**
     * The identifier of item
     */
    id: string;
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
    permissions: string[];
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

