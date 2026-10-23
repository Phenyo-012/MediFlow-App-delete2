package com.medicationinventory.model.enums;



/**
 * Permission identifiers shared by profile mapping, presentation gates, and trusted admin APIs. These
 * values describe individual capabilities; the server remains authoritative when a request changes shared data.
 */
public enum Permission
{

    VIEW_MEDICINES("VIEW_MEDICINES", "View Medicines"),
    CREATE_MEDICINE("CREATE_MEDICINE", "Create Medicine"),
    UPDATE_MEDICINE("UPDATE_MEDICINE", "Update Medicine"),
    DEACTIVATE_MEDICINE("DEACTIVATE_MEDICINE", "Deactivate Medicine"),
    VIEW_BATCHES("VIEW_BATCHES", "View Batches"),
    RECEIVE_STOCK("RECEIVE_STOCK", "Receive Stock"),
    CREATE_ISSUE("CREATE_ISSUE", "Create Issue"),
    CREATE_ADJUSTMENT("CREATE_ADJUSTMENT", "Create Adjustment"),
    CREATE_CORRECTION("CREATE_CORRECTION", "Create Correction"),
    OVERRIDE_FEFO("OVERRIDE_FEFO", "Override FEFO"),
    VIEW_INVENTORY_HISTORY("VIEW_INVENTORY_HISTORY", "View Inventory History"),
    VIEW_STOCK_LEVELS("VIEW_STOCK_LEVELS", "View Stock Levels"),
    VIEW_ABC_VED("VIEW_ABC_VED", "View ABC-VED"),
    MANAGE_ABC_VED("MANAGE_ABC_VED", "Manage ABC-VED"),
    VIEW_ALERTS("VIEW_ALERTS", "View Alerts"),
    ACKNOWLEDGE_ALERTS("ACKNOWLEDGE_ALERTS", "Acknowledge Alerts"),
    GENERATE_REPORTS("GENERATE_REPORTS", "Generate Reports"),
    VIEW_AUDIT("VIEW_AUDIT", "View Audit"),
    VIEW_USERS("VIEW_USERS", "View Users"),
    CREATE_USERS("CREATE_USERS", "Create Users"),
    UPDATE_USERS("UPDATE_USERS", "Update Users"),
    DEACTIVATE_USERS("DEACTIVATE_USERS", "Deactivate Users"),
    MANAGE_USER_ROLES("MANAGE_USER_ROLES", "Manage User Roles"),
    MANAGE_USER_PERMISSIONS("MANAGE_USER_PERMISSIONS", "Manage User Permissions"),
    MANAGE_SETTINGS("MANAGE_SETTINGS", "Manage Settings");

    private final String permissionId;
    private final String displayName;
    Permission(String permissionId, String displayName)
    {
        this.permissionId = permissionId;
        this.displayName = displayName;
    }

    public String getPermissionId()
    {
        return permissionId;
    }
    public String getDisplayName()
    {
        return displayName;
    }

    public static Permission fromPermissionId(String permissionId)
    {
        if (permissionId == null)
        {
            return null;
        }
        for (Permission permission : values())
        {
            if(permission.permissionId.equals(permissionId))
            {
                return permission;
            }
        }
        return null;
    }
}
