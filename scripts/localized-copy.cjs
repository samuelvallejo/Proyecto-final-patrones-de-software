/* Localized artifact and test data are separate from executable source. */
const fs = require('node:fs');
const path = require('node:path');

function load(filename) {
  const values = JSON.parse(fs.readFileSync(path.join(__dirname, 'locales', filename), 'utf8'));
  return {
    text(key) {
      if (typeof values[key] !== 'string') throw new Error(`Missing localized copy: ${key}`);
      return values[key];
    }
  };
}

module.exports = {load};
