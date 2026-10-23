package com.medicationinventory.model.enums;



/**
 * Application role identifiers and display labels used in profile mapping and user administration. The role
 * label alone does not replace the profile's explicit permissions or trusted backend authorization.
 */
public enum UserRole
{
    PHARMACY_ASSISTANT("pharmacy_assistant", "Pharmacy Assistant"),
    STOCK_CONTROLLER("stock_controller", "Stock Controller"),
    PHARMACIST("pharmacist", "Pharmacist"),
    PHARMACY_MANAGER_ADMIN("pharmacy_manager_admin", "Pharmacy Manager / Administrator");

    private final String roleId;
    private final String displayName;

    UserRole(String roleId, String displayName)
    {
        this.roleId = roleId;
        this.displayName = displayName;
    }

    public String getRoleId()
    {
        return roleId;
    }

    public String getDisplayName()
    {
        return displayName;
    }


    public static UserRole fromRoleId(String roleId)
    {
        if (roleId == null)
        {
            return null;
        }

        for (UserRole role : values())
        {
            if (role.roleId.equals(roleId))
            {
                return role;
            }
        }
        return null;
    }

}
