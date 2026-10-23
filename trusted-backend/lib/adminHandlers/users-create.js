/**
 * HTTP handler for the users-create administration endpoint. It translates the
 * request into a call to the trusted application service.
 *
 * Keep authentication, allowed methods and error responses explicit so a malformed
 * request does not accidentally reach a write operation.
 */
const {
  getAuth,
} = require("firebase-admin/auth");

const {
  getAdminFirestore,
} = require("../firebaseAdmin");

const {
  createApplicationUser,
} = require("../userCreationService");

const {
  UserAdminHttpError,
} = require("../userAdminAuthorization");


/**
 * Checks bearer token before the value reaches trusted storage. Invalid or
 * ambiguous input should fail here.
 */
function parseBearerToken(
    request,
) {
  const authorization =
    request.headers.authorization;


  if (
    typeof authorization !== "string" ||
    !authorization.startsWith("Bearer ")
  ) {
    throw new UserAdminHttpError(
        401,
        "AUTHENTICATION_REQUIRED",
        "Authentication is required.",
    );
  }


  const token =
    authorization
        .substring(
            "Bearer ".length,
        )
        .trim();


  if (!token) {
    throw new UserAdminHttpError(
        401,
        "AUTHENTICATION_REQUIRED",
        "Authentication is required.",
    );
  }


  return token;
}


/**
 * Checks body before the value reaches trusted storage. Invalid or ambiguous input
 * should fail here.
 */
function parseBody(
    request,
) {
  if (
    request.body &&
    typeof request.body === "object" &&
    !Array.isArray(request.body)
  ) {
    return request.body;
  }


  if (
    typeof request.body === "string"
  ) {
    try {
      return JSON.parse(
          request.body,
      );
    } catch {
      throw new UserAdminHttpError(
          400,
          "INVALID_REQUEST_BODY",
          "The request body is invalid.",
      );
    }
  }


  throw new UserAdminHttpError(
      400,
      "INVALID_REQUEST_BODY",
      "User details are required.",
  );
}


module.exports =
  /**
   * Accepts only POST requests, verifies the Firebase ID token and administrator
   * permission, then validates and creates the requested Firebase account and
   * application profile through the user-admin service. The service result is
   * mapped to the endpoint response; failures are converted by the handler's
   * error path.
   *
   * @param {Object} request Vercel request with the new-user payload
   * @param {Object} response Vercel response object
   * @return {Promise<*>} response produced after the create attempt
   */
  async function handler(
      request,
      response,
  ) {
    if (request.method !== "POST") {
      response
          .status(405)
          .json({
            code:
              "METHOD_NOT_ALLOWED",

            message:
              "Only POST is supported.",
          });

      return;
    }


    try {
      /*
       * Ensure Firebase Admin is initialized before
       * attempting token verification.
       */
      getAdminFirestore();


      const token =
        parseBearerToken(
            request,
        );


      let decodedToken;


      try {
        decodedToken =
          await getAuth()
              .verifyIdToken(
                  token,
                  true,
              );
      } catch (error) {
        console.error(
            "[create-user] token verification failed",
            {
              code:
                error && error.code
                  ? error.code
                  : null,
            },
        );


        response
            .status(401)
            .json({
              code:
                "INVALID_AUTH_TOKEN",

              message:
                "Authentication could not be verified.",
            });

        return;
      }


      const body =
        parseBody(
            request,
        );


      const user =
        await createApplicationUser(
            decodedToken.uid,
            body,
        );


      response
          .status(201)
          .json({
            user,
          });
    } catch (error) {
      if (
        error instanceof UserAdminHttpError
      ) {
        response
            .status(
                error.statusCode,
            )
            .json({
              code:
                error.code,

              message:
                error.message,
            });

        return;
      }

      console.error(
          "[create-user] creation failed",
          {
            code:
              error && error.code
                ? String(error.code)
                : "UNEXPECTED_ERROR",
          },
      );

     response
         .status(500)
         .json({
           code:
             "USER_CREATION_FAILED",

           message:
             "The user account could not be created.",
         });
       }
     };