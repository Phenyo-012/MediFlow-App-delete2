package com.medicationinventory.model.domain;

import com.medicationinventory.model.enums.Permission;
import com.medicationinventory.model.enums.UserRole;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;



/**
 * Application-side profile associated with a Firebase UID. It carries role and
 * explicit permissions used by UI and repository gates, plus active state and
 * the time the profile was validated. A profile can support cached/offline UI
 * decisions, but trusted backend authorization remains authoritative for cloud
 * mutations.
 */
public class UserProfile
{
    private final String uid;
    private final String firstName;
    private  final String lastName;
    private final String email;
    private final UserRole role;
    private final Set<Permission> permissions;
    private final boolean active;
    private long lastValidatedAt;


    public UserProfile(String uid,
                       String firstName,
                       String lastName,
                       String email,
                       UserRole role,
                       Set<Permission> permissions,
                       boolean active,
                       long lastValidatedAt
    )
    {
        this.uid = uid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.role = role;

        if (permissions == null || permissions.isEmpty())
        {
            this.permissions = Collections.emptySet();
        }
        else
        {
            this.permissions = Collections.unmodifiableSet(EnumSet.copyOf(permissions));
        }

        this.active = active;
        this.lastValidatedAt = lastValidatedAt;
    }

    public String getUid()
    {
        return uid;
    }
    public String getFirstName()
    {
        return firstName;
    }
    public String getLastName()
    {
        return lastName;
    }
    public String getEmail()
    {
        return email;
    }
    public UserRole getRole()
    {
        return role;
    }

    public Set<Permission> getPermissions()
    {
        return permissions;
    }
    public boolean isActive()
    {
        return active;
    }
    public long getLastValidatedAt()
    {
        return lastValidatedAt;
    }

    public String getFullName()
    {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();

        return (first + " " + last).trim();
    }

    public boolean hasPermission(Permission permission)
    {
        return permission != null && permissions.contains(permission);
    }


    public boolean can(Permission permission)
    {
        return active && hasPermission(permission);
    }

}
