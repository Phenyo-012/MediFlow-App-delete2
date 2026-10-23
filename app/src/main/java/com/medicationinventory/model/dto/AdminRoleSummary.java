package com.medicationinventory.model.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;




/**
 * Carries Admin Role Summary data across the Android and trusted-backend boundary.
 */
public class AdminRoleSummary
{
    private final String roleId;

    private final String roleName;

    private final List<String>
            baselinePermissions;



    public AdminRoleSummary(
            String roleId,
            String roleName,
            List<String> baselinePermissions)
    {
        if (roleId == null
                ||
                roleId.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Role ID is required."
            );
        }


        if (roleName == null
                ||
                roleName.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Role name is required."
            );
        }


        this.roleId =
                roleId.trim();


        this.roleName =
                roleName.trim();


        List<String> safePermissions =
                new ArrayList<>();


        if (baselinePermissions != null)
        {
            for (String permission : baselinePermissions)
            {
                if (permission == null
                        ||
                        permission.trim().isEmpty())
                {
                    continue;
                }


                String normalized =
                        permission.trim();


                if (!safePermissions.contains(
                        normalized
                ))
                {
                    safePermissions.add(
                            normalized
                    );
                }
            }
        }


        this.baselinePermissions =
                Collections.unmodifiableList(
                        safePermissions
                );
    }


    public String getRoleId()
    {
        return roleId;
    }


    public String getRoleName()
    {
        return roleName;
    }


    public List<String> getBaselinePermissions()
    {
        return baselinePermissions;
    }
}