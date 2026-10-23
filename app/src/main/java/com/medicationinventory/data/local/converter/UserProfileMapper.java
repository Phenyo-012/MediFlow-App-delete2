package com.medicationinventory.data.local.converter;

import com.medicationinventory.data.local.entity.UserProfileEntity;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;
import com.medicationinventory.model.enums.UserRole;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.EnumSet;
import java.util.Set;



/**
 * Maps between Room UserProfileEntity records and UserProfile domain objects, including serialized permissions.
 */
public final class UserProfileMapper
{

    private UserProfileMapper()
    {
        // Prevent instantiation
    }


    public static UserProfile toDomain(UserProfileEntity entity)
    {
        if (entity == null)
        {
            return null;
        }

        return new UserProfile(
                entity.getUid(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                UserRole.fromRoleId(entity.getRoleId()),
                permissionsFromJson(entity.getPermissionJson()),
                entity.isActive(),
                entity.getLastValidatedAt()
        );
    }


    public static UserProfileEntity toEntity(UserProfile profile)
    {
        if (profile == null)
        {
            return null;
        }

        String roleId = profile.getRole() == null ? null : profile.getRole().getRoleId();

        return new UserProfileEntity(
                profile.getUid(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getEmail(),
                roleId,
                permissionsToJson(profile.getPermissions()),
                profile.isActive(),
                profile.getLastValidatedAt()
        );
    }


    private static Set<Permission> permissionsFromJson(String json)
    {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);

        if (json == null || json.trim().isEmpty())
        {
            return permissions;
        }

        try
        {
            JSONArray array = new JSONArray(json);

            for (int i = 0; i < array.length(); i++)
            {
                String permissionId = array.optString(i, null);

                Permission permission = Permission.fromPermissionId(permissionId);

                if (permission != null)
                {
                    permissions.add(permission);
                }
            }
        }
        catch (JSONException ignored)
        {
            // Invalid stored permission JSON should not crash the app.
        }
        return permissions;
    }


    private static String permissionsToJson(Set<Permission> permissions)
    {
        JSONArray array = new JSONArray();

        if (permissions == null)
        {
            return array.toString();
        }

        for (Permission permission : permissions)
        {
            if (permission != null)
            {
                array.put(permission.getPermissionId());
            }
        }

        return array.toString();
    }
}
