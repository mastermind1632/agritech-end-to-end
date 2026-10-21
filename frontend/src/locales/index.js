// Translation tables. Each entry maps the English text to [Afrikaans, Sepedi, Xitsonga, isiZulu].
// DRAFT translations: please have first-language speakers review them before launch.
//   ui.js       screens, buttons, forms and messages of the web app
//   content.js  Business Builder steps and export requirements seeded in the database
//   server.js   labels, notifications and error messages sent by the API
// To add a language, add a column to every row and a code to LANG_COLUMNS (and to LANGUAGES in lib/i18n.js).
import ui from './ui.js';
import content from './content.js';
import server from './server.js';

export const LANG_COLUMNS = { af: 0, nso: 1, ts: 2, zu: 3 };

const PHRASES = Object.assign(Object.create(null), server, content, ui);
export default PHRASES;
