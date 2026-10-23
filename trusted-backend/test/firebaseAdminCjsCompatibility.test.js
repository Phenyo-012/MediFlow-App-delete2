
/**
 * Checks Firebase Admin exports through the CommonJS module shape used by this backend.
 */

 "use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {spawnSync} = require("node:child_process");
const path = require("node:path");

test(
    "Firebase Admin loads without ESM interop",
    () => {
      const backendRoot = path.resolve(
          __dirname,
          "..",
      );

      const result = spawnSync(
          process.execPath,
          [
            "--no-experimental-require-module",
            "-e",
            "require('./api/admin-router.js'); " +
            "console.log('ROLES_MODULE_LOADED');",
          ],
          {
            cwd: backendRoot,
            encoding: "utf8",
            timeout: 15000,
          },
      );

      assert.ifError(result.error);
      assert.equal(
          result.status,
          0,
          result.stderr,
      );

      assert.match(
          result.stdout,
          /ROLES_MODULE_LOADED/,
      );
    },
);
