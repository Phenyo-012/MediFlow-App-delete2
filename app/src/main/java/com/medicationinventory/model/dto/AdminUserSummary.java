package com.medicationinventory.model.dto;



/**
 * Carries Admin User Summary data across the Android and trusted-backend boundary.
 */
public class AdminUserSummary
{
    private final String uid;

    private final String firstName;
    private final String lastName;
    private final String email;

    private final String roleId;

    private final boolean active;

    private final long createdAt;
    private final long updatedAt;



    public AdminUserSummary(
            String uid,
            String firstName,
            String lastName,
            String email,
            String roleId,
            boolean active,
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

        this.active =
                active;

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


    public boolean isActive()
    {
        return active;
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