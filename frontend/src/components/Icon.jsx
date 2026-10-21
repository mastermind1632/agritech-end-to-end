export default function Icon({ name }) {
  const icons = {
    grid: '⌂', wallet: '▣', market: '▤', users: '♧', spark: '✦', bell: '◔', user: '●', plus: '＋', arrow: '→', leaf: '❧',
    logout: '↪', chat: '✉', briefcase: '⚑', globe: '✈', receipt: '≣', folder: '▭', scale: '⚖', shield: '⛨', gear: '⚙',
    chart: '▥', store: '⌸', basket: '⛟'
  };
  return <span className="icon" aria-hidden="true">{icons[name] || '•'}</span>;
}
