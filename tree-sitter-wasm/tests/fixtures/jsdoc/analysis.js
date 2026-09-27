const misleading = "/** @typedef {string} Fake */";
/* @typedef {string} AlsoFake */
/**
 * @typedef {Object} Root
 * @property {string} "display-name"
 * @property {?string} [nickname="guest"]
 * @property {Array.<Item>} items
 * @property {Object.<string, boolean>} flags
 * @property {object} settings
 * @property {number} settings.retries
 * @property {string} [settings.label]
 */
/** @typedef {{id: number, name: string}} Item */
/** @typedef {Root[]} Users */
function untouched() { return misleading; }
