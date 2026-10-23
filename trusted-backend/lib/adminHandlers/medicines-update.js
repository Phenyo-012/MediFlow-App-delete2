/**
 * HTTP handler for the medicines-update administration endpoint. It translates the
 * request into a call to the trusted application service.
 *
 * Keep authentication, allowed methods and error responses explicit so a malformed
 * request does not accidentally reach a write operation.
 */
"use strict";

const {updateMedicine} = require("../medicineMasterAdminService");
const {authenticate, requireBody, handleError} =
  require("../medicineMasterHttp");
const {ApplicationHttpError} = require("../applicationAuthorization");

module.exports = async function handler(request, response) {
  if (request.method !== "PUT") {
    response.setHeader("Allow", "PUT");
    return response.status(405).json({
      code: "METHOD_NOT_ALLOWED", message: "Only PUT is supported.",
    });
  }
  try {
    const uid = await authenticate(request);
    const id = request.query && request.query.medicineId;
    if (typeof id !== "string") {
      throw new ApplicationHttpError(400, "INVALID_MEDICINE_ID",
          "A medicine ID is required.");
    }
    const body = requireBody(request);
    const result = await updateMedicine(uid, id, body);
    return response.status(200).json(result);
  } catch (error) {
    return handleError(error, response);
  }
};
