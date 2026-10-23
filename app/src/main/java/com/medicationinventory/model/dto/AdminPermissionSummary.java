package com.medicationinventory.model.dto;



/**
 * Carries Admin Permission Summary data across the Android and trusted-backend boundary.
 */
public class AdminPermissionSummary
{
    private final String
            permissionId;

    private final String
            displayName;



    public AdminPermissionSummary(
            String permissionId,
            String displayName)
    {
        if (permissionId == null
                ||
                permissionId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Permission ID is required."
            );
        }


        if (displayName == null
                ||
                displayName.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Permission display name is required."
            );
        }


        this.permissionId =
                permissionId.trim();

        this.displayName =
                displayName.trim();
    }



    public String getPermissionId()
    {
        return permissionId;
    }


    public String getDisplayName()
    {
        return displayName;
    }
}