/**
 * Defines application permission identifiers and display metadata used by administration endpoints.
 */

const PERMISSIONS = Object.freeze([
  {
    permissionId:
      "VIEW_MEDICINES",

    displayName:
      "View Medicines",
  },
  {
    permissionId:
      "CREATE_MEDICINE",

    displayName:
      "Create Medicine",
  },
  {
    permissionId:
      "UPDATE_MEDICINE",

    displayName:
      "Update Medicine",
  },
  {
    permissionId:
      "DEACTIVATE_MEDICINE",

    displayName:
      "Deactivate Medicine",
  },
  {
    permissionId:
      "VIEW_BATCHES",

    displayName:
      "View Batches",
  },
  {
    permissionId:
      "RECEIVE_STOCK",

    displayName:
      "Receive Stock",
  },
  {
    permissionId:
      "CREATE_ISSUE",

    displayName:
      "Create Issue",
  },
  {
    permissionId:
      "CREATE_ADJUSTMENT",

    displayName:
      "Create Adjustment",
  },
  {
    permissionId:
      "CREATE_CORRECTION",

    displayName:
      "Create Correction",
  },
  {
    permissionId:
      "OVERRIDE_FEFO",

    displayName:
      "Override FEFO",
  },
  {
    permissionId:
      "VIEW_INVENTORY_HISTORY",

    displayName:
      "View Inventory History",
  },
  {
    permissionId:
      "VIEW_STOCK_LEVELS",

    displayName:
      "View Stock Levels",
  },
  {
    permissionId:
      "VIEW_ABC_VED",

    displayName:
      "View ABC-VED",
  },
  {
    permissionId:
      "MANAGE_ABC_VED",

    displayName:
      "Manage ABC-VED",
  },
  {
    permissionId:
      "VIEW_ALERTS",

    displayName:
      "View Alerts",
  },
  {
    permissionId:
      "ACKNOWLEDGE_ALERTS",

    displayName:
      "Acknowledge Alerts",
  },
  {
    permissionId:
      "GENERATE_REPORTS",

    displayName:
      "Generate Reports",
  },
  {
    permissionId:
      "VIEW_AUDIT",

    displayName:
      "View Audit",
  },
  {
    permissionId:
      "VIEW_USERS",

    displayName:
      "View Users",
  },
  {
    permissionId:
      "CREATE_USERS",

    displayName:
      "Create Users",
  },
  {
    permissionId:
      "UPDATE_USERS",

    displayName:
      "Update Users",
  },
  {
    permissionId:
      "DEACTIVATE_USERS",

    displayName:
      "Deactivate Users",
  },
  {
    permissionId:
      "MANAGE_USER_ROLES",

    displayName:
      "Manage User Roles",
  },
  {
    permissionId:
      "MANAGE_USER_PERMISSIONS",

    displayName:
      "Manage User Permissions",
  },
  {
    permissionId:
      "MANAGE_SETTINGS",

    displayName:
      "Manage Settings",
  },
]);


const VALID_PERMISSION_IDS =
  new Set(
      PERMISSIONS.map(
          (permission) =>
            permission.permissionId,
      ),
  );


function isValidPermissionId(
    permissionId,
) {
  return (
    typeof permissionId === "string" &&
    VALID_PERMISSION_IDS.has(
        permissionId.trim(),
    )
  );
}


function getPermissionCatalogue() {
  return PERMISSIONS.map(
      (permission) => ({
        permissionId:
          permission.permissionId,

        displayName:
          permission.displayName,
      }),
  );
}


module.exports = {
  PERMISSIONS,
  VALID_PERMISSION_IDS,
  isValidPermissionId,
  getPermissionCatalogue,
};