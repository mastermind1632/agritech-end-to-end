"""Generate dark-theme overrides for every hard-coded colour in the app's stylesheets.

Reads the light stylesheets, finds each declaration that uses a literal colour, maps the colour by its role
(background, border, text, shadow) to a dark-theme equivalent that keeps its hue, and writes rules scoped to
:root[data-theme="dark"] so they win over the originals. Areas that are already dark (the sidebar, the
sign-in artwork) are left alone. Run again after editing the light styles:

    python3 tools/darkgen.py src/styles.css src/platform.css src/chat.css src/ai.css src/market-intelligence.css > src/theme-dark.generated.css
"""
import colorsys
import re
import sys

NAMED = {'white': (255, 255, 255, 1.0), 'black': (0, 0, 0, 1.0)}
COLOR_RE = re.compile(r'#[0-9a-fA-F]{3,8}\b|rgba?\([^)]*\)|\bwhite\b|\bblack\b')
EXCLUDE = re.compile(r'\.sidebar|(^|[\s,>])nav\b|\.nav-section|\.brand|\.mini-profile|\.side-label|\.auth-art|\.hero-|\.auth-brand|\.skip-link|\.scrim|\.farm-scene|\.deployment-notice')
SCOPE = ':root[data-theme="dark"]'


def parse(c):
    c = c.strip()
    if c in NAMED:
        return NAMED[c]
    if c.startswith('#'):
        h = c[1:]
        if len(h) in (3, 4):
            h = ''.join(ch * 2 for ch in h)
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        a = int(h[6:8], 16) / 255 if len(h) == 8 else 1.0
        return r, g, b, a
    nums = re.findall(r'[\d.]+%?', c)
    vals = []
    for n in nums[:3]:
        vals.append(float(n[:-1]) * 2.55 if n.endswith('%') else float(n))
    a = float(nums[3]) if len(nums) > 3 else 1.0
    return vals[0], vals[1], vals[2], a


def fmt(r, g, b, a):
    r, g, b = (max(0, min(255, round(x))) for x in (r, g, b))
    if a >= 0.999:
        return '#%02x%02x%02x' % (r, g, b)
    return 'rgba(%d,%d,%d,%s)' % (r, g, b, ('%.3f' % a).rstrip('0').rstrip('.'))


def hls(r, g, b):
    return colorsys.rgb_to_hls(r / 255, g / 255, b / 255)


def from_hls(h, l, s):
    r, g, b = colorsys.hls_to_rgb(h, max(0, min(1, l)), max(0, min(1, s)))
    return r * 255, g * 255, b * 255


def map_color(c, role):
    r, g, b, a = parse(c)
    h, l, s = hls(r, g, b)
    if role == 'shadow':
        if l < 0.5:
            return fmt(0, 0, 0, min(0.6, a * 3 + 0.12))
        return fmt(r, g, b, a * 0.25)
    if role == 'text':
        if l <= 0.55:
            nl = 0.93 - l * 0.62
            ns = min(s, 0.55) if s > 0.15 else s
            return fmt(*from_hls(h, nl, ns), a)
        return c  # light text (e.g. white on a coloured button) stays
    if role == 'border':
        if l >= 0.7:
            nl = 0.2 + (1 - l) * 0.45
            return fmt(*from_hls(h, nl, min(s, 0.22)), a)
        if l <= 0.3 and s < 0.3:
            return fmt(*from_hls(h, 0.55, s), a)
        return c
    # background
    if a < 0.5 and l >= 0.6:
        # faint light overlays become faint light-on-dark overlays
        return fmt(*from_hls(h, 0.6, min(s, 0.4)), a * 0.6)
    if a < 0.5 and l < 0.4:
        return fmt(0, 0, 0, min(0.7, a * 2 + 0.2))
    if l >= 0.97 and s < 0.2:
        return 'var(--surface)'
    if l >= 0.78:
        nl = 0.115 + (0.97 - l) * 0.55
        ns = min(s, 0.32)
        return fmt(*from_hls(h, nl, ns), a)
    if l >= 0.4:
        if s > 0.3:
            return fmt(*from_hls(h, l * 0.62, s * 0.8), a)  # coloured fills: deepen a little
        return fmt(*from_hls(h, 0.24 + (0.78 - l) * 0.25, s), a)
    return c  # dark fills (buttons, chips) already read well on a dark page


def role_of(prop):
    p = prop.strip().lower()
    if p.startswith('background'):
        return 'bg'
    if 'shadow' in p:
        return 'shadow'
    if p.startswith(('border', 'outline')) or p in ('column-rule',):
        return 'border'
    if p in ('color', 'fill', 'stroke', 'caret-color', 'accent-color', 'text-decoration-color', '-webkit-text-fill-color'):
        return 'text'
    return None


def convert_value(prop, value):
    role = role_of(prop)
    if not role:
        return None
    out = value
    if role == 'bg':
        out = re.sub(r'var\(--green\)', 'var(--green-strong)', out)
    if role == 'border' and 'shadow' not in prop:
        pass
    if role == 'shadow' and 'inset' in value and re.search(r'#[0-9a-f]{3,6}', value, re.I):
        # inset accent bars: treat like a border colour
        out = COLOR_RE.sub(lambda m: map_color(m.group(0), 'border'), out)
    else:
        out = COLOR_RE.sub(lambda m: map_color(m.group(0), role), out)
    return out if out != value else None


def split_decls(body):
    decls, depth, cur = [], 0, ''
    for ch in body:
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
        if ch == ';' and depth == 0:
            decls.append(cur)
            cur = ''
        else:
            cur += ch
    if cur.strip():
        decls.append(cur)
    return [d for d in decls if ':' in d]


def blocks(css):
    """Yield (prelude, body) at the top level, keeping nested bodies as text."""
    i, n = 0, len(css)
    while i < n:
        j = css.find('{', i)
        if j < 0:
            return
        prelude = css[i:j]
        semi = prelude.rfind(';')
        if semi >= 0:  # @import ...; or stray declarations
            prelude = prelude[semi + 1:]
        depth, k = 1, j + 1
        while k < n and depth:
            if css[k] == '{':
                depth += 1
            elif css[k] == '}':
                depth -= 1
            k += 1
        yield prelude.strip(), css[j + 1:k - 1]
        i = k


def scope_selectors(sel):
    parts = [p.strip() for p in re.split(r',(?![^(]*\))', sel) if p.strip()]
    keep = []
    for p in parts:
        if EXCLUDE.search(p) or p.startswith(':root'):
            continue
        if p.startswith(('html', 'body')):
            keep.append(f'{SCOPE} {p}' if p.startswith('body') else p.replace('html', f'html{SCOPE[5:]}', 1))
        else:
            keep.append(f'{SCOPE} {p}')
    return ','.join(keep)


def process(css):
    out = []
    for prelude, body in blocks(css):
        if prelude.startswith('@media') or prelude.startswith('@supports'):
            inner = process(body)
            if inner.strip():
                out.append(f'{prelude}{{\n{inner}}}')
            continue
        if prelude.startswith(('@keyframes', '@font-face', '@-webkit-keyframes', '@page')):
            continue
        sel = scope_selectors(prelude)
        if not sel:
            continue
        new = []
        for d in split_decls(body):
            prop, value = d.split(':', 1)
            if prop.strip().startswith('--'):
                continue
            nv = convert_value(prop, value)
            if nv is not None:
                new.append(f'{prop.strip()}:{nv.strip()}')
        if new:
            out.append(f'{sel}{{{";".join(new)}}}')
    return '\n'.join(out) + '\n'


if __name__ == '__main__':
    print('/* GENERATED by tools/darkgen.py from the light stylesheets - do not edit by hand. */')
    for path in sys.argv[1:]:
        css = re.sub(r'/\*.*?\*/', '', open(path, encoding='utf-8').read(), flags=re.S)
        print(f'/* --- {path.split("/")[-1]} --- */')
        print(process(css))
