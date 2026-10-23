
/**
 * Exposes the shared application-permission check under the user-administration service names.
 */
const {
  requireApplicationPermission,
  ApplicationHttpError,
} = require("./applicationAuthorization");


const requireUserAdminPermission =
  requireApplicationPermission;


const UserAdminHttpError =
  ApplicationHttpError;


module.exports = {
  requireUserAdminPermission,
  UserAdminHttpError,
};