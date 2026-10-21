// Animated farm illustration for the sign-in screen: a rising sun, drifting clouds, rolling fields,
// vegetables that pop out of the soil and sway, and seeds floating on the breeze.
// Pure SVG + CSS (see .farm-scene in motion.css), so it costs no image downloads on low data.
// Everything is decorative (aria-hidden) and stands still when the device asks for reduced motion.

const VEG = {
  tomato: (
    <>
      <ellipse cx="50" cy="58" rx="36" ry="32" fill="#e24a3b" />
      <path d="M26 52c4-10 14-16 24-16s20 6 24 16" fill="none" stroke="#b9322a" strokeWidth="2.5" opacity=".45" />
      <ellipse cx="35" cy="49" rx="9" ry="5.5" fill="#fff" opacity=".35" transform="rotate(-32 35 49)" />
      <path d="M50 27l6 9 12-3-8 9 10 7-14-3-6 9-6-9-14 3 10-7-8-9 12 3z" fill="#3f9a4f" />
      <rect x="48" y="15" width="4" height="15" rx="2" fill="#2f7a3c" />
    </>
  ),
  carrot: (
    <>
      <path d="M36 30c8-6 20-6 28 0L53 92c-1.5 4-4.5 4-6 0z" fill="#f08a24" />
      <path d="M41 42h9M45 54h9M44 66h7M48 78h5" stroke="#c76611" strokeWidth="2.6" strokeLinecap="round" />
      <path d="M50 30C42 14 33 9 25 9c10 6 16 13 20 21zM50 30c-1-14 2-23 7-28-1 10-2 19-3 28zM50 30c8-12 16-18 25-18-9 5-15 11-20 18z" fill="#4caf50" />
    </>
  ),
  maize: (
    <>
      <ellipse cx="50" cy="43" rx="16" ry="33" fill="#f6c745" />
      <path d="M37 23q13-4 26 0M35 33q15-4 30 0M34 43q16-4 32 0M35 53q15-4 30 0M38 63q12-4 24 0" stroke="#d6a11e" strokeWidth="2" fill="none" />
      <path d="M43 12v60M50 10v64M57 12v60" stroke="#d6a11e" strokeWidth="1.6" opacity=".7" />
      <path d="M50 94C24 78 26 46 31 28c4 22 10 36 19 44z" fill="#5fae4e" />
      <path d="M50 94c26-16 24-48 19-66-4 22-10 36-19 44z" fill="#47973d" />
    </>
  ),
  cabbage: (
    <>
      <circle cx="50" cy="55" r="36" fill="#7cc36a" />
      <path d="M50 19c-20 8-26 28-18 50M50 19c20 8 26 28 18 50M22 46c12 2 22 10 28 28M78 46c-12 2-22 10-28 28" fill="none" stroke="#4f9a45" strokeWidth="3" strokeLinecap="round" />
      <circle cx="50" cy="52" r="15" fill="#a6db8f" />
      <path d="M50 37v30" stroke="#6bb35b" strokeWidth="2.5" strokeLinecap="round" />
    </>
  ),
  chilli: (
    <>
      <path d="M30 26c10 2 14 10 14 22 0 18 10 32 30 40-26 4-44-12-46-34-1-14-3-22 2-28z" fill="#d93a2f" />
      <path d="M34 34c3 4 4 9 4 16" stroke="#fff" strokeWidth="3" strokeLinecap="round" opacity=".3" fill="none" />
      <path d="M28 27c-2-8 2-14 10-16M30 24c6-4 12-3 15 2" stroke="#3f8f3f" strokeWidth="5" strokeLinecap="round" fill="none" />
    </>
  ),
  butternut: (
    <>
      <path d="M44 14h12c2 14 2 22 6 30 10 8 16 18 16 28 0 14-12 22-28 22S22 86 22 72c0-10 6-20 16-28 4-8 4-16 6-30z" fill="#efb35a" />
      <circle cx="50" cy="74" r="10" fill="#d99437" opacity=".6" />
      <path d="M38 34c2 6 2 10 0 14" stroke="#fff" strokeWidth="3" opacity=".3" strokeLinecap="round" fill="none" />
      <rect x="46" y="6" width="8" height="11" rx="3" fill="#7a5a2c" />
    </>
  ),
  onion: (
    <>
      <path d="M50 18c4 12 30 26 30 48 0 16-14 26-30 26S20 82 20 66c0-22 26-36 30-48z" fill="#9a4f8c" />
      <path d="M50 26c-8 16-16 26-16 42M50 26c8 16 16 26 16 42" stroke="#7a3a6e" strokeWidth="2.4" fill="none" opacity=".7" />
      <path d="M50 20c-2-8 0-14 4-18M50 20c2-6 6-10 10-12" stroke="#4caf50" strokeWidth="4" strokeLinecap="round" fill="none" />
      <path d="M44 92c2 4 10 4 12 0" stroke="#d9c7a3" strokeWidth="3" strokeLinecap="round" fill="none" />
    </>
  ),
  pea: (
    <>
      <path d="M12 58c18-30 58-38 78-26-10 28-50 42-78 26z" fill="#62b04f" />
      <circle cx="34" cy="52" r="7" fill="#9bd77f" /><circle cx="50" cy="46" r="7.5" fill="#9bd77f" /><circle cx="66" cy="41" r="7" fill="#9bd77f" />
      <path d="M12 58c22 4 52-4 78-26" stroke="#3f8f3f" strokeWidth="2.5" fill="none" />
      <path d="M90 32c4-6 4-12 0-16" stroke="#3f8f3f" strokeWidth="3" strokeLinecap="round" fill="none" />
    </>
  )
};

function Veg({ kind, className, style }) {
  return (
    <svg className={`veg ${className || ''}`} style={style} viewBox="0 0 100 100" aria-hidden="true">{VEG[kind]}</svg>
  );
}

// Vegetables that pop out of the field: [kind, left %, bottom %, size px, delay s]
const HARVEST = [
  ['cabbage', 6, 7, 96, 0.2], ['carrot', 21, 13, 78, 0.45], ['maize', 33, 4, 112, 0.7], ['tomato', 50, 10, 84, 0.95],
  ['butternut', 63, 3, 98, 1.2], ['onion', 77, 11, 74, 1.45], ['chilli', 88, 5, 76, 1.7]
];
// Vegetables drifting slowly up the right-hand side: [kind, left %, size, delay s, duration s]
const DRIFT = [['pea', 74, 54, 0, 26], ['tomato', 86, 40, 7, 30], ['carrot', 66, 46, 14, 28], ['maize', 92, 50, 20, 32]];
// Seeds and leaves on the breeze: [top %, size, delay s, duration s]
const SEEDS = [[18, 10, 0, 19], [34, 7, 4, 23], [48, 9, 9, 21], [26, 6, 13, 25], [58, 8, 2, 27], [40, 11, 16, 22]];

export default function FarmScene() {
  return (
    <div className="farm-scene" aria-hidden="true">
      <div className="scene-glow" />
      <svg className="scene-sky" viewBox="0 0 600 800" preserveAspectRatio="xMidYMid slice">
        <g className="sun">
          <g className="sun-rays">
            {Array.from({ length: 12 }).map((_, i) => (
              <rect key={i} x="-4" y="-98" width="8" height="26" rx="4" transform={`rotate(${i * 30})`} />
            ))}
          </g>
          <circle r="54" className="sun-disc" />
        </g>
        <g className="cloud cloud-a"><ellipse cx="0" cy="0" rx="52" ry="18" /><ellipse cx="-22" cy="-12" rx="26" ry="20" /><ellipse cx="18" cy="-16" rx="30" ry="24" /></g>
        <g className="cloud cloud-b"><ellipse cx="0" cy="0" rx="40" ry="14" /><ellipse cx="-14" cy="-10" rx="20" ry="16" /><ellipse cx="14" cy="-12" rx="22" ry="18" /></g>
      </svg>

      {SEEDS.map(([top, size, delay, dur], i) => (
        <span key={i} className={`seed ${i % 2 ? 'leafy' : ''}`} style={{ top: `${top}%`, width: size, height: size, animationDelay: `${delay}s`, animationDuration: `${dur}s` }} />
      ))}

      {DRIFT.map(([kind, left, size, delay, dur], i) => (
        <div key={i} className="drift" style={{ left: `${left}%`, width: size, height: size, animationDelay: `${delay}s`, animationDuration: `${dur}s` }}>
          <Veg kind={kind} />
        </div>
      ))}

      <svg className="scene-fields" viewBox="0 0 600 260" preserveAspectRatio="none">
        <path className="hill hill-far" d="M0 120C90 70 170 72 260 100s190 30 340-20V260H0z" />
        <path className="hill hill-mid" d="M0 160c110-50 220-46 330-14s170 28 270-6V260H0z" />
        <path className="hill hill-near" d="M0 200c120-36 260-40 380-16s160 14 220 0V260H0z" />
        <g className="rows">
          {[0, 1, 2, 3, 4].map((i) => (
            <path key={i} d={`M-20 ${222 + i * 9}C150 ${196 + i * 10} 380 ${198 + i * 10} 620 ${214 + i * 9}`} />
          ))}
        </g>
      </svg>

      <div className="harvest">
        {HARVEST.map(([kind, left, bottom, size, delay], i) => (
          <div key={kind} className="harvest-item" style={{ left: `${left}%`, bottom: `${bottom}%`, width: size, height: size, '--delay': `${delay}s`, '--sway': `${4 + (i % 3) * 1.5}s` }}>
            <Veg kind={kind} />
            <span className="soil" />
          </div>
        ))}
      </div>
    </div>
  );
}
