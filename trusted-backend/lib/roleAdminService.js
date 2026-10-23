/**
 * Applies protected role changes in Firestore and records the resulting permissions and audit state.
 */

const {
  FieldValue,
} = require("firebase-admin/firestore");

const {
  requireUserAdminPermission,
  UserAdminHttpError,
} = require("./userAdminAuthorization");

const {
  writeAuditRecord,
  AUDIT_ACTIONS,
} = require("./audit");


const MANAGE_USER_ROLES =
  "MANAGE_USER_ROLES";


/**
 * Changes another user's role in a Firestore transaction, replacing explicit permissions
 * with the selected role's baseline and writing an audit record with the prior state.
 */
async function setApplicationUserRole(
    authenticatedUid,
    targetUid,
    roleId,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        MANAGE_USER_ROLES,
    );


  const cleanTargetUid =
    requireTargetUid(
        targetUid,
    );


  const cleanRoleId =
    requireRoleId(
        roleId,
    );



  if (
    authenticatedUid ===
    cleanTargetUid
  ) {
    throw new UserAdminHttpError(
        409,
        "SELF_ROLE_CHANGE_NOT_ALLOWED",
        "You cannot change your own role.",
    );
  }


  const targetUserRef =
    context.db
      .collection("users")
      .doc(cleanTargetUid);


  const roleRef =
    context.db
      .collection("roles")
      .doc(cleanRoleId);


  return context.db.runTransaction(
      async (transaction) => {

        const userSnapshot =
          await transaction.get(
              targetUserRef,
          );


        const roleSnapshot =
          await transaction.get(
              roleRef,
          );


        if (!userSnapshot.exists) {
          throw new UserAdminHttpError(
              404,
              "USER_NOT_FOUND",
              "The user account was not found.",
          );
        }


        if (!roleSnapshot.exists) {
          throw new UserAdminHttpError(
              404,
              "ROLE_NOT_FOUND",
              "The selected role was not found.",
          );
        }


        const user =
          userSnapshot.data() || {};


        const role =
          roleSnapshot.data() || {};



        if (
          typeof user.uid === "string" &&
          user.uid.trim() !== "" &&
          user.uid.trim() !== cleanTargetUid
        ) {
          throw new UserAdminHttpError(
              409,
              "USER_ID_MISMATCH",
              "The user profile is inconsistent.",
          );
        }


        const previousRoleId =
          normalizeNullableText(
              user.roleId,
          );


        const previousPermissions =
          normalizePermissions(
              user.permissions,
          );



        const newPermissions =
          normalizePermissions(
              role.baselinePermissions,
          );



        if (
          previousRoleId ===
          cleanRoleId
        ) {
          return {
            changed: false,

            roleId:
              cleanRoleId,

            permissions:
              previousPermissions,
          };
        }


        transaction.update(
            targetUserRef,
            {
              roleId:
                cleanRoleId,

              permissions:
                newPermissions,

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
                AUDIT_ACTIONS.ROLE_CHANGED,

              entityType:
                "USER",

              entityId:
                cleanTargetUid,

              performedBy:
                authenticatedUid,

              details: {
                previousRoleId,

                newRoleId:
                  cleanRoleId,

                previousPermissions,

                newPermissions,

                permissionsResetToRoleBaseline:
                  true,
              },
            },
        );


        return {
          changed: true,

          roleId:
            cleanRoleId,

          permissions:
            newPermissions,
        };
      },
  );
}


async function listApplicationRoles(
    authenticatedUid,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        MANAGE_USER_ROLES,
    );


  const snapshot =
    await context.db
      .collection("roles")
      .get();


  const roles = [];


  snapshot.forEach(
      (documentSnapshot) => {
        roles.push(
            mapRole(
                documentSnapshot,
            ),
        );
      },
  );


  roles.sort(
      (left, right) =>
        left.roleName.localeCompare(
            right.roleName,
            "en",
            {
              sensitivity: "base",
            },
        ),
  );


  return {
    roles,

    count:
      roles.length,
  };
}


function mapRole(
    documentSnapshot,
) {
  const role =
    documentSnapshot.data() || {};


  const roleId =
    documentSnapshot.id;


  const roleName =
    normalizeText(
        role.roleName,
    );


  if (!roleName) {
    throw new UserAdminHttpError(
        500,
        "INVALID_ROLE",
        `Role ${roleId} does not contain a valid role name.`,
    );
  }


  return {
    roleId,

    roleName,

    baselinePermissions:
      normalizePermissions(
          role.baselinePermissions,
      ),
  };
}


function requireTargetUid(
    value,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new UserAdminHttpError(
        400,
        "USER_ID_REQUIRED",
        "A target user is required.",
    );
  }


  return value.trim();
}


function requireRoleId(
    value,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new UserAdminHttpError(
        400,
        "ROLE_ID_REQUIRED",
        "A role must be selected.",
    );
  }


  return value.trim();
}


function normalizePermissions(
    value,
) {
  if (
    !Array.isArray(
        value,
    )
  ) {
    return [];
  }


  return [
    ...new Set(
        value
          .filter(
              (permission) =>
                typeof permission ===
                "string",
          )
          .map(
              (permission) =>
                permission.trim(),
          )
          .filter(
              (permission) =>
                permission !== "",
          ),
    ),
  ].sort();
}


function normalizeText(
    value,
) {
  if (
    typeof value !==
    "string"
  ) {
    return null;
  }


  const normalized =
    value.trim();


  return normalized === ""
    ? null
    : normalized;
}


function normalizeNullableText(
    value,
) {
  if (
    typeof value !==
    "string"
  ) {
    return null;
  }


  const normalized =
    value.trim();


  return normalized === ""
    ? null
    : normalized;
}


module.exports = {
  listApplicationRoles,
  setApplicationUserRole,
};