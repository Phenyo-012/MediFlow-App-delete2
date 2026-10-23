/**
 * Serverless HTTP entry point for health. The handler is intentionally thin so
 * common rules remain in the service layer.
 *
 * Validate the HTTP request before delegating and make the response status match
 * the actual outcome.
 */
module.exports = async function handler(
    request,
    response
) {
  if (request.method !== "GET") {
    response.setHeader(
        "Allow",
        "GET"
    );

    return response
        .status(405)
        .json({
          error: "METHOD_NOT_ALLOWED"
        });
  }

  return response
      .status(200)
      .json({
        service:
          "digital-medication-inventory-backend",
        status:
          "ok"
      });
};