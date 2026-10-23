const js = require("@eslint/js");
const globals = require("globals");

/**
 * Centralizes lint rules for the trusted backend source and test files.
 */
module.exports = [
  {
    ignores: [
      "node_modules/**",
      ".vercel/**",
    ],
  },

  js.configs.recommended,

  {
    files: [
      "**/*.js",
    ],

    languageOptions: {
      ecmaVersion: "latest",

      sourceType: "commonjs",

      globals: {
        ...globals.node,
      },
    },

    rules: {
      "no-unused-vars": [
        "error",
        {
          argsIgnorePattern: "^_",
        },
      ],
    },
  },
];