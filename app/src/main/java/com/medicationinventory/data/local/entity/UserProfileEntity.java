package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;


/**
 * Caches a Firebase UID's profile fields, role, serialized permissions, active state, and last
 * server-validation time. The profile supports offline display and policy decisions, but backend
 * endpoints re-read authoritative user data before privileged mutations.
 */
@Entity(tableName = "user_profiles")
public class UserProfileEntity
{
    @PrimaryKey
    @NonNull
    private String uid;
    private String firstName;
    private String lastName;
    private String email;
    private String roleId;
    private String permissionJson;
    private boolean isActive;
    private long lastValidatedAt;


    public UserProfileEntity(@NonNull String uid,
                             String firstName,
                             String lastName,
                             String email,
                             String roleId,
                             String permissionJson,
                             boolean isActive,
                             long lastValidatedAt)
    {
        this.uid = uid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.roleId = roleId;
        this.permissionJson = permissionJson;
        this.isActive = isActive;
        this.lastValidatedAt = lastValidatedAt;
    }
    @NonNull
    public String getUid()
    {
        return uid;
    }
    public void setUid(@NonNull String uid)
    {
        this.uid = uid;
    }
    public String getFirstName()
    {
        return firstName;
    }
    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }
    public String getLastName()
    {
        return lastName;
    }
    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }
    public String getEmail()
    {
        return email;
    }
    public void setEmail(String email)
    {
        this.email = email;
    }
    public String getRoleId()
    {
        return roleId;
    }
    public void setRoleId(String roleId)
    {
        this.roleId = roleId;
    }

    public String getPermissionJson()
    {
        return permissionJson;
    }

    public void setPermissionJson(String permissionJson)
    {
        this.permissionJson = permissionJson;
    }
    public boolean isActive()
    {
        return isActive;
    }
    public void setActive(boolean active)
    {
        isActive = active;
    }
    public long getLastValidatedAt()
    {
        return lastValidatedAt;
    }
    public void setLastValidatedAt(long lastValidatedAt)
    {
        this.lastValidatedAt = lastValidatedAt;
    }
}
