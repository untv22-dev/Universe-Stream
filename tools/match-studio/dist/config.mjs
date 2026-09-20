// Deployment settings. Edit this file after deploying worker/; nothing else needs changing.
//
// Leave `extractEndpoint` empty and the "read a table from an image" step stays hidden, so the
// page keeps working as a purely static tool with no backend.
export const config = {
  // Full URL of the worker's /extract route, e.g. 'https://universe-match-studio.<you>.workers.dev/extract'
  extractEndpoint: '',

  // Must equal the worker's CLIENT_TOKEN secret. This is not a security boundary on its own — it
  // is visible to anyone who opens the page — it only stops the endpoint being used by other
  // sites. The worker's origin allowlist and daily cap are what actually bound the spend.
  extractToken: '',
};
