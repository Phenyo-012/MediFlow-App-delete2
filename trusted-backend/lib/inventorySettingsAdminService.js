/**
 * Validates and audits shared inventory setting changes while handling legacy stored fields.
 */

const {
  FieldValue,
} = require("firebase-admin/firestore");

const {
  requireApplicationPermission,
  ApplicationHttpError,
} = require("./applicationAuthorization");

const {
  writeAuditRecord,
  AUDIT_ACTIONS,
} = require("./audit");


const MANAGE_SETTINGS =
  "MANAGE_SETTINGS";


const INVENTORY_SETTING_ID =
  "inventory";


const MAX_ANDROID_INT =
  2147483647;


/**
 * Creates the trusted settings mutation with injectable authorization, audit, and timestamp
 * providers for tests. The production endpoint uses server authorization and Firestore's
 * serverTimestamp; clients cannot supply those values.
 *
 * @param {object} overrides optional dependency providers
 * @return {{setInventorySettings: Function}} settings mutation API
 */
function createInventorySettingsAdminService(
    overrides = {},
) {
  const authorize =
    overrides.requireApplicationPermission ||
    requireApplicationPermission;


  const auditWriter =
    overrides.writeAuditRecord ||
    writeAuditRecord;


  const serverTimestamp =
    overrides.serverTimestamp ||
    (() => FieldValue.serverTimestamp());



  /**
   * Requires MANAGE_SETTINGS, validates the warning-day value, and writes the canonical setting
   * with actor/server timestamp metadata. The transaction also removes the legacy misspelled field
   * when present and writes an audit record atomically; a no-op canonical value returns unchanged
   * unless legacy data still needs cleanup.
   *
   * @param {string} authenticatedUid verified actor UID
   * @param {number} nearExpiryWarningDays accepted warning window
   * @return {Promise<object>} setting ID, changed/created flags, and canonical value
   */
  async function setInventorySettings(
      authenticatedUid,
      nearExpiryWarningDays,
  ) {

    const context =
      await authorize(
          authenticatedUid,
          MANAGE_SETTINGS,
      );


    const cleanWarningDays =
      requireNearExpiryWarningDays(
          nearExpiryWarningDays,
      );


    const settingsRef =
      context.db
        .collection(
            "settings",
        )
        .doc(
            INVENTORY_SETTING_ID,
        );


    const auditId =
      context.db
        .collection(
            "auditLogs",
        )
        .doc()
        .id;


    return context.db.runTransaction(
        async (transaction) => {
          const settingsSnapshot =
            await transaction.get(
                settingsRef,
            );


          const existing =
            settingsSnapshot.exists
              ? settingsSnapshot.data() || {}
              : null;


          const previousWarningDays =
            existing === null
              ? null
              : normalizeStoredNearExpiryWarningDays(
                  existing.nearExpiryWarningDays,
              );

          const hasLegacyMisspelledWarningDays =
            existing !== null &&
            Object.prototype.hasOwnProperty.call(
                existing,
                "nearexpiryWarningDays",
            );

          if (
            existing !== null &&
            previousWarningDays !== null &&
            previousWarningDays === cleanWarningDays &&
            !hasLegacyMisspelledWarningDays
          ) {
            return {
              changed:
                false,

              created:
                false,

              settingId:
                INVENTORY_SETTING_ID,

              nearExpiryWarningDays:
                cleanWarningDays,
            };
          }


          const created =
            existing === null;


          const settingsData = {
            nearExpiryWarningDays:
              cleanWarningDays,

            updatedAt:
              serverTimestamp(),

            updatedBy:
              context.uid,
          };


          if (created) {
            transaction.create(
                settingsRef,
                settingsData,
            );
          } else {
            transaction.update(
               settingsRef,
               {
                 ...settingsData,


                 nearexpiryWarningDays:
                   FieldValue.delete(),
               },
           );
          }



          auditWriter(
              transaction,
              context.db,
              {
                auditId,

                actionType:
                  AUDIT_ACTIONS
                    .SETTING_CHANGED,

                entityType:
                  "SYSTEM_SETTING",

                entityId:
                  INVENTORY_SETTING_ID,

                performedBy:
                  context.uid,

                details: {
                  settingId:
                    INVENTORY_SETTING_ID,

                  settingName:
                    "nearExpiryWarningDays",

                  previousValue:
                    previousWarningDays,

                  previousValueValid:
                    existing === null ||
                    previousWarningDays !== null,

                  newValue:
                    cleanWarningDays,

                  legacyFieldRemoved:
                      hasLegacyMisspelledWarningDays

                },
              },
          );


          return {
            changed:
              true,

            created,

            settingId:
              INVENTORY_SETTING_ID,

            nearExpiryWarningDays:
              cleanWarningDays,
          };
        },
    );
  }


  return {
    setInventorySettings,
  };
}


const defaultService =
  createInventorySettingsAdminService();


/**
 * Default service entry point used by the admin route; delegates to the configured
 * settings service so the HTTP layer shares the same validation and audit path.
 */
async function setInventorySettings(
    authenticatedUid,
    nearExpiryWarningDays,
) {
  return defaultService
    .setInventorySettings(
        authenticatedUid,
        nearExpiryWarningDays,
    );
}


function requireNearExpiryWarningDays(
    value,
) {
  if (
    !Number.isInteger(
        value,
    )
  ) {
    throw new ApplicationHttpError(
        400,
        "INVALID_NEAR_EXPIRY_WARNING_DAYS",
        "Near-expiry warning days must be a whole number.",
    );
  }


  if (
    value < 0 ||
    value > MAX_ANDROID_INT
  ) {
    throw new ApplicationHttpError(
        400,
        "INVALID_NEAR_EXPIRY_WARNING_DAYS",
        "Near-expiry warning days are outside the supported range.",
    );
  }


  return value;
}


function normalizeStoredNearExpiryWarningDays(
    value,
) {
  if (
    !Number.isInteger(
        value,
    ) ||
    value < 0 ||
    value > MAX_ANDROID_INT
  ) {

    return null;
  }


  return value;
}


module.exports = {
  setInventorySettings,
  createInventorySettingsAdminService,
};