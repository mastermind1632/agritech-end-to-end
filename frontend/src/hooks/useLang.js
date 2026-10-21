import { useEffect, useState } from 'react';
import { getLang, listeners, setLang, translate } from '../lib/i18n';

/** Current interface language; every component using it re-renders when the language changes. */
export default function useLang() {
  const [lang, set] = useState(getLang);
  useEffect(() => {
    listeners.add(set);
    return () => listeners.delete(set);
  }, []);
  return { lang, setLang, t: (key, vars) => translate(lang, key, vars) };
}
