/**
 * Implements protected user listing, detail reads, and account status changes against Firestore.
 */

const {
  requireUserAdminPermission,
  UserAdminHttpError,
} = require("./userAdminAuthorization");

const {
  FieldValue,
} = require("firebase-admin/firestore");

const {
  writeAuditRecord,
  AUDIT_ACTIONS,
} = require("./audit");

const VIEW_USERS =
  "VIEW_USERS";

const DEACTIVATE_USERS =
  "DEACTIVATE_USERS";


async function listApplicationUsers(
    authenticatedUid,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        VIEW_USERS,
    );


  const snapshot =
    await context.db
      .collection("users")
      .limit(500)
      .get();


  const users = [];


  snapshot.forEach(
      (documentSnapshot) => {
        users.push(
            mapUserForList(
                documentSnapshot,
            ),
        );
      },
  );


  users.sort(
      compareUsers,
  );


  return {
    users,

    count:
      users.length,
  };
}


function mapUserForList(
    documentSnapshot,
) {
  const profile =
    documentSnapshot.data() || {};


  const uid =
    normalizeText(
        profile.uid,
    ) ||
    documentSnapshot.id;


  if (uid !==
      documentSnapshot.id) {
    throw new UserAdminHttpError(
        500,
        "INVALID_USER_PROFILE",
        "A user profile has an invalid account identifier.",
    );
  }


  return {
    uid,

    firstName:
      normalizeText(
          profile.firstName,
      ),

    lastName:
      normalizeText(
          profile.lastName,
      ),

    email:
      normalizeText(
          profile.email,
      ),

    roleId:
      normalizeText(
          profile.roleId,
      ),

    isActive:
      profile.isActive === true,

    createdAt:
      timestampMillis(
          profile.createdAt,
      ),

    updatedAt:
      timestampMillis(
          profile.updatedAt,
      ),
  };
}



function compareUsers(
    left,
    right,
) {
  const leftKey =
    userSortKey(
        left,
    );


  const rightKey =
    userSortKey(
        right,
    );


  return leftKey.localeCompare(
      rightKey,
      "en",
      {
        sensitivity:
          "base",
      },
  );
}


function userSortKey(
    user,
) {
  return [
    user.lastName || "",
    user.firstName || "",
    user.email || "",
    user.uid || "",
  ]
    .join("|");
}



function timestampMillis(
    value,
) {
  if (!value) {
    return null;
  }


  if (typeof value.toMillis ===
      "function") {
    const millis =
      value.toMillis();


    return Number.isFinite(millis)
      ? millis
      : null;
  }


  return null;
}


function normalizeText(
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


async function getApplicationUserDetail(
    authenticatedUid,
    targetUid,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        VIEW_USERS,
    );


  const uid =
    requireTargetUid(
        targetUid,
    );


  const userRef =
    context.db
      .collection("users")
      .doc(uid);


  const snapshot =
    await userRef.get();


  if (!snapshot.exists) {
    throw new UserAdminHttpError(
        404,
        "USER_NOT_FOUND",
        "The selected user account was not found.",
    );
  }


  const profile =
    snapshot.data() || {};


  const profileUid =
    normalizeText(
        profile.uid,
    ) ||
    snapshot.id;


  if (profileUid !==
      snapshot.id) {
    throw new UserAdminHttpError(
        500,
        "INVALID_USER_PROFILE",
        "The selected user profile is invalid.",
    );
  }


  return {
    uid:
      profileUid,

    firstName:
      normalizeText(
          profile.firstName,
      ),

    lastName:
      normalizeText(
          profile.lastName,
      ),

    email:
      normalizeText(
          profile.email,
      ),

    roleId:
      normalizeText(
          profile.roleId,
      ),

    permissions:
      normalizePermissions(
          profile.permissions,
      ),

    isActive:
      profile.isActive === true,

    createdAt:
      timestampMillis(
          profile.createdAt,
      ),

    updatedAt:
      timestampMillis(
          profile.updatedAt,
      ),

    isSelf:
      authenticatedUid ===
      profileUid,
  };
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


function requireTargetUid(
    value,
) {
  if (typeof value !==
      "string" ||
      value.trim() === "") {
    throw new UserAdminHttpError(
        400,
        "INVALID_USER_ID",
        "A user ID is required.",
    );
  }


  return value.trim();
}


async function setApplicationUserActiveState(
    authenticatedUid,
    targetUid,
    active,
) {
  const context =
    await requireUserAdminPermission(
        authenticatedUid,
        DEACTIVATE_USERS,
    );


  const uid =
    requireTargetUid(
        targetUid,
    );


  if (typeof active !==
      "boolean") {
    throw new UserAdminHttpError(
        400,
        "INVALID_ACTIVE_STATE",
        "The requested account state is invalid.",
    );
  }


  if (authenticatedUid === uid &&
      active === false) {
    throw new UserAdminHttpError(
        409,
        "SELF_DEACTIVATION_NOT_ALLOWED",
        "You cannot deactivate your own signed-in account.",
    );
  }


  const userRef =
    context.db
      .collection("users")
      .doc(uid);


  return context.db.runTransaction(
      async (transaction) => {
        const snapshot =
          await transaction.get(
              userRef,
          );


        if (!snapshot.exists) {
          throw new UserAdminHttpError(
              404,
              "USER_NOT_FOUND",
              "The selected user account was not found.",
          );
        }


        const profile =
          snapshot.data() || {};


        const profileUid =
          normalizeText(
              profile.uid,
          ) ||
          snapshot.id;


        if (profileUid !==
            snapshot.id) {
          throw new UserAdminHttpError(
              500,
              "INVALID_USER_PROFILE",
              "The selected user profile is invalid.",
          );
        }


        const previousActive =
          profile.isActive === true;



        if (previousActive ===
            active) {
          return {
            changed: false,

            user:
              mapUserAccessResult(
                  snapshot.id,
                  profile,
              ),
          };
        }


        transaction.update(
            userRef,
            {
              isActive:
                active,

              updatedAt:
                FieldValue.serverTimestamp(),
            },
        );


        const actionType =
          active
            ? AUDIT_ACTIONS.USER_REACTIVATED
            : AUDIT_ACTIONS.USER_DEACTIVATED;


        const auditRef =
          context.db
            .collection("auditLogs")
            .doc();


        writeAuditRecord(
            transaction,
            context.db,
            {
              auditId:
                auditRef.id,

              actionType,

              entityType:
                "USER",

              entityId:
                uid,

              performedBy:
                authenticatedUid,

              details: {
                previousActive,

                newActive:
                  active,
              },
            },
        );


        return {
          changed: true,

          user: {
            ...mapUserAccessResult(
                snapshot.id,
                profile,
            ),

            isActive:
              active,
          },
        };
      },
  );
}


function mapUserAccessResult(
    uid,
    profile,
) {
  return {
    uid,

    firstName:
      normalizeText(
          profile.firstName,
      ),

    lastName:
      normalizeText(
          profile.lastName,
      ),

    email:
      normalizeText(
          profile.email,
      ),

    roleId:
      normalizeText(
          profile.roleId,
      ),

    permissions:
      normalizePermissions(
          profile.permissions,
      ),

    isActive:
      profile.isActive === true,
  };
}

module.exports = {
  listApplicationUsers,
  getApplicationUserDetail,
  setApplicationUserActiveState,
};