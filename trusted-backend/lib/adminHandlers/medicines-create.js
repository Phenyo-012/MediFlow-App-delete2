/**
 * HTTP handler for the medicines-create administration endpoint. It translates the
 * request into a call to the trusted application service.
 *
 * Keep authentication, allowed methods and error responses explicit so a malformed
 * request does not accidentally reach a write operation.
 */
"use strict";

const {createMedicine} = require("../medicineMasterAdminService");
const {authenticate, requireBody, handleError} =
  require("../medicineMasterHttp");

module.exports = async function handler(request, response) {
  if (request.method !== "POST") {
    response.setHeader("Allow", "POST");
    return response.status(405).json({
      code: "METHOD_NOT_ALLOWED", message: "Only POST is supported.",
    });
  }
  try {
    const uid = await authenticate(request);
    const body = requireBody(request);
    const result = await createMedicine(uid, body);
    return response.status(201).json(result);
  } catch (error) {
    return handleError(error, response);
  }
};
