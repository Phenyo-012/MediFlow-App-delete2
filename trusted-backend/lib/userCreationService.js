
const {
  FieldValue,
} = require("firebase-admin/firestore");

const {
  getAuth,
} = require("firebase-admin/auth");

const {
  requireUserAdminPermission,
  UserAdminHttpError,
} = require("./userAdminAuthorization");

const {
  writeAuditRecord,
  AUDIT_ACTIONS,
} = require("./audit");



function normalizeRequiredText(
    value,
    fieldName,
) {
  if (
    typeof value !== "string" ||
    !value.trim()
  ) {
    throw new UserAdminHttpError(
        400,
        "INVALID_USER_DATA",
        `${fieldName} is required.`,
    );
  }

  return value.trim();
}



function normalizePermissions(
    value,
) {
  if (!Array.isArray(value)) {
    return [];
  }


  return [
    ...new Set(
        value
            .filter(
                (permission) =>
                  typeof permission === "string" &&
                  permission.trim(),
            )
            .map(
                (permission) =>
                  permission.trim(),
            ),
    ),
  ].sort();
}



function normalizeEmail(
    value,
) {
  const email =
    normalizeRequiredText(
        value,
        "Email",
    ).toLowerCase();


  if (
    !email.includes("@") ||
    email.startsWith("@") ||
    email.endsWith("@")
  ) {
    throw new UserAdminHttpError(
        400,
        "INVALID_EMAIL",
        "A valid email address is required.",
    );
  }


  return email;
}



function normalizePassword(
    value,
) {
  if (
    typeof value !== "string" ||
    value.length < 6
  ) {
    throw new UserAdminHttpError(
        400,
        "INVALID_PASSWORD",
        "The temporary password must contain at least 6 characters.",
    );
  }


  return value;
}



/**
 * Creates the Firebase identity and application profile after validating account fields
 * and requested permissions; audit and cleanup behavior keep partial failures visible.
 */
async function createApplicationUser(
    authenticatedUid,
    input,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        "CREATE_USERS",
    );


  if (
    !input ||
    typeof input !== "object" ||
    Array.isArray(input)
  ) {
    throw new UserAdminHttpError(
        400,
        "INVALID_REQUEST_BODY",
        "User details are required.",
    );
  }


  const firstName =
    normalizeRequiredText(
        input.firstName,
        "First name",
    );


  const lastName =
    normalizeRequiredText(
        input.lastName,
        "Last name",
    );


  const email =
    normalizeEmail(
        input.email,
    );


  const password =
    normalizePassword(
        input.password,
    );


  const roleId =
    normalizeRequiredText(
        input.roleId,
        "Role",
    );



  const roleRef =
    context.db
        .collection("roles")
        .doc(roleId);


  const roleSnapshot =
    await roleRef.get();


  if (!roleSnapshot.exists) {
    throw new UserAdminHttpError(
        400,
        "INVALID_ROLE",
        "The selected role does not exist.",
    );
  }


  const role =
    roleSnapshot.data() || {};


  const baselinePermissions =
    normalizePermissions(
        role.baselinePermissions,
    );


  let authUser;


  try {
    authUser =
      await getAuth()
          .createUser({
            email,
            password,

            displayName:
              `${firstName} ${lastName}`.trim(),

            disabled: false,
          });
  } catch (error) {
    if (
      error &&
      error.code === "auth/email-already-exists"
    ) {
      throw new UserAdminHttpError(
          409,
          "EMAIL_ALREADY_IN_USE",
          "A user with this email address already exists.",
      );
    }


    if (
      error &&
      error.code === "auth/invalid-email"
    ) {
      throw new UserAdminHttpError(
          400,
          "INVALID_EMAIL",
          "The email address is invalid.",
      );
    }


    if (
      error &&
      error.code === "auth/invalid-password"
    ) {
      throw new UserAdminHttpError(
          400,
          "INVALID_PASSWORD",
          "The temporary password is invalid.",
      );
    }


    throw error;
  }


  const newUid =
    authUser.uid;


  const userRef =
    context.db
        .collection("users")
        .doc(newUid);


  try {
    await context.db.runTransaction(
        async (transaction) => {
          const existingSnapshot =
            await transaction.get(
                userRef,
            );


          if (existingSnapshot.exists) {
            throw new UserAdminHttpError(
                409,
                "USER_ALREADY_EXISTS",
                "The application user profile already exists.",
            );
          }


          transaction.create(
              userRef,
              {
                uid:
                  newUid,

                firstName,

                lastName,

                email,

                roleId,

                permissions:
                  baselinePermissions,

                isActive:
                  true,

                createdAt:
                  FieldValue.serverTimestamp(),

                updatedAt:
                  FieldValue.serverTimestamp(),
              },
          );

          const auditId =
            context.db
                .collection("auditLogs")
                .doc()
                .id;

          writeAuditRecord(
              transaction,
              context.db,
              {
                 auditId,

                actionType:
                  AUDIT_ACTIONS.USER_CREATED,

                entityType:
                  "USER",

                entityId:
                  newUid,

                performedBy:
                  authenticatedUid,

                details: {
                  email,

                  roleId,

                  initialPermissions:
                    baselinePermissions,
                },
              },
          );
        },
    );
  } catch (error) {

    try {
      await getAuth()
          .deleteUser(
              newUid,
          );
    } catch {
      // Preserve the original failure.
    }


    throw error;
  }


  return {
    uid:
      newUid,

    firstName,

    lastName,

    email,

    roleId,

    permissions:
      baselinePermissions,

    isActive:
      true,
  };
}


module.exports = {
  createApplicationUser,
};