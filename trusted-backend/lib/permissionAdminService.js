/**
 * Lists permissions and transactionally changes another user's explicit permission grants.
 */

const {
  FieldValue,
} = require("firebase-admin/firestore");

const {
  requireUserAdminPermission,
  UserAdminHttpError,
} = require("./userAdminAuthorization");

const {
  getPermissionCatalogue,
  isValidPermissionId,
} = require("./permissionCatalogue");

const {
  writeAuditRecord,
  AUDIT_ACTIONS,
} = require("./audit");


const MANAGE_USER_PERMISSIONS =
  "MANAGE_USER_PERMISSIONS";


async function listApplicationPermissions(
    authenticatedUid,
) {
  await requireUserAdminPermission(
      authenticatedUid,
      MANAGE_USER_PERMISSIONS,
  );


  const permissions =
    getPermissionCatalogue();


  return {
    permissions,

    count:
      permissions.length,
  };
}


/**
 * Adds or removes one explicit permission for another user. The transaction reads the
 * latest profile and records the change so concurrent administration cannot silently win.
 */
async function setApplicationUserPermission(
    authenticatedUid,
    targetUid,
    permissionId,
    granted,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        MANAGE_USER_PERMISSIONS,
    );


  const cleanTargetUid =
    requireTargetUid(
        targetUid,
    );


  const cleanPermissionId =
    requirePermissionId(
        permissionId,
    );


  if (typeof granted !==
      "boolean") {
    throw new UserAdminHttpError(
        400,
        "GRANTED_REQUIRED",
        "Permission state must be provided.",
    );
  }


  if (
    authenticatedUid ===
    cleanTargetUid
  ) {
    throw new UserAdminHttpError(
        409,
        "SELF_PERMISSION_CHANGE_NOT_ALLOWED",
        "You cannot change your own permissions.",
    );
  }


  if (!isValidPermissionId(
      cleanPermissionId,
  )) {
    throw new UserAdminHttpError(
        400,
        "INVALID_PERMISSION",
        "The selected permission is not valid.",
    );
  }


  const targetUserRef =
    context.db
      .collection("users")
      .doc(cleanTargetUid);


  return context.db.runTransaction(
      async (transaction) => {
        const userSnapshot =
          await transaction.get(
              targetUserRef,
          );


        if (!userSnapshot.exists) {
          throw new UserAdminHttpError(
              404,
              "USER_NOT_FOUND",
              "The user account was not found.",
          );
        }


        const user =
          userSnapshot.data() || {};


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

        const roleId =
          normalizeNullableText(
              user.roleId,
          );


        if (!roleId) {
          throw new UserAdminHttpError(
              409,
              "USER_ROLE_REQUIRED",
              "The user does not have a valid assigned role.",
          );
        }


        const roleRef =
          context.db
            .collection("roles")
            .doc(roleId);


        const roleSnapshot =
          await transaction.get(
              roleRef,
          );


        if (!roleSnapshot.exists) {
          throw new UserAdminHttpError(
              409,
              "ROLE_NOT_FOUND",
              "The user's assigned role could not be found.",
          );
        }


        const previousPermissions =
          normalizePermissions(
              user.permissions,
          );


        const alreadyGranted =
          previousPermissions.includes(
              cleanPermissionId,
          );


        if (
          granted === alreadyGranted
        ) {
          return {
            changed:
              false,

            permissionId:
              cleanPermissionId,

            granted:
              alreadyGranted,

            permissions:
              previousPermissions,
          };
        }


        const permissionSet =
          new Set(
              previousPermissions,
          );


        if (granted) {
          permissionSet.add(
              cleanPermissionId,
          );
        } else {
          permissionSet.delete(
              cleanPermissionId,
          );
        }


        const newPermissions =
          Array.from(
              permissionSet,
          ).sort();


        transaction.update(
            targetUserRef,
            {
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
                granted
                  ? AUDIT_ACTIONS
                    .PERMISSION_GRANTED
                  : AUDIT_ACTIONS
                    .PERMISSION_REVOKED,

              entityType:
                "USER",

              entityId:
                cleanTargetUid,

              performedBy:
                authenticatedUid,

              details: {
                permission:
                  cleanPermissionId,

                granted,

                roleId:
                  normalizeNullableText(
                      user.roleId,
                  ),
              },
            },
        );


        return {
          changed:
            true,

          permissionId:
            cleanPermissionId,

          granted,

          permissions:
            newPermissions,
        };
      },
  );
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


function requirePermissionId(
    value,
) {
  if (
    typeof value !== "string" ||
    value.trim() === ""
  ) {
    throw new UserAdminHttpError(
        400,
        "PERMISSION_ID_REQUIRED",
        "A permission must be selected.",
    );
  }


  return value.trim();
}


function normalizePermissions(
    value,
) {
  if (!Array.isArray(
      value,
  )) {
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


function normalizeNullableText(
    value,
) {
  if (typeof value !==
      "string") {
    return null;
  }


  const normalized =
    value.trim();


  return normalized === ""
    ? null
    : normalized;
}


module.exports = {
  listApplicationPermissions,
  setApplicationUserPermission,
};