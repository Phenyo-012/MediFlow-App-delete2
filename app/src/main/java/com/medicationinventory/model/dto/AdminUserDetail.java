package com.medicationinventory.model.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;




/**
 * Carries Admin User Detail data across the Android and trusted-backend boundary.
 */
public class AdminUserDetail
{
    private final String uid;

    private final String firstName;
    private final String lastName;
    private final String email;

    private final String roleId;

    private final List<String>
            permissions;

    private final boolean active;
    private final boolean self;

    private final long createdAt;
    private final long updatedAt;



    public AdminUserDetail(
            String uid,
            String firstName,
            String lastName,
            String email,
            String roleId,
            List<String> permissions,
            boolean active,
            boolean self,
            long createdAt,
            long updatedAt)
    {
        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            throw new IllegalArgumentException(
                    "User ID is required."
            );
        }


        this.uid =
                uid.trim();

        this.firstName =
                normalize(
                        firstName
                );

        this.lastName =
                normalize(
                        lastName
                );

        this.email =
                normalize(
                        email
                );

        this.roleId =
                normalize(
                        roleId
                );


        List<String> safePermissions =
                new ArrayList<>();


        if (permissions != null)
        {
            for (String permission : permissions)
            {
                String normalized =
                        normalize(
                                permission
                        );


                if (normalized != null
                        &&
                        !safePermissions.contains(
                                normalized
                        ))
                {
                    safePermissions.add(
                            normalized
                    );
                }
            }
        }


        this.permissions =
                Collections.unmodifiableList(
                        safePermissions
                );


        this.active =
                active;

        this.self =
                self;

        this.createdAt =
                Math.max(
                        0L,
                        createdAt
                );

        this.updatedAt =
                Math.max(
                        0L,
                        updatedAt
                );
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


    public String getRoleId()
    {
        return roleId;
    }


    public List<String> getPermissions()
    {
        return permissions;
    }


    public boolean isActive()
    {
        return active;
    }


    public boolean isSelf()
    {
        return self;
    }


    public long getCreatedAt()
    {
        return createdAt;
    }


    public long getUpdatedAt()
    {
        return updatedAt;
    }


    public String getFullName()
    {
        String first =
                firstName == null
                        ? ""
                        : firstName;


        String last =
                lastName == null
                        ? ""
                        : lastName;


        String fullName =
                (
                        first
                                + " "
                                + last
                )
                        .trim();


        return fullName.isEmpty()
                ? "Unnamed user"
                : fullName;
    }


    private String normalize(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim();


        return normalized.isEmpty()
                ? null
                : normalized;
    }
}