"""API compatibility shims: reproducible fixture/APK/DEX link inventory (stdlib only)."""
import gzip
import hashlib
import json
import pathlib
import re
import struct
import subprocess
import urllib.request
import urllib.error
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
TEMP = pathlib.Path(r"C:\Users\paele\AppData\Local\Temp\opencode\extdex")
BASE = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/"
DEXDUMP = pathlib.Path(r"C:\Users\paele\AppData\Local\Android\Sdk\build-tools\35.0.0\dexdump.exe")

def varint(data, pos):
    value = shift = 0
    while True:
        b = data[pos]; pos += 1
        value |= (b & 127) << shift
        if b < 128: return value, pos
        shift += 7

def fields(data):
    pos = 0
    while pos < len(data):
        tag, pos = varint(data, pos)
        if tag & 7 == 0:
            value, pos = varint(data, pos)
        elif tag & 7 == 2:
            size, pos = varint(data, pos)
            value = data[pos:pos + size]; pos += size
        else: raise ValueError(tag)
        yield tag >> 3, value

def dex_inventory(data):
    def u32(p): return struct.unpack_from('<I', data, p)[0]
    def table(p, width):
        size, offset = struct.unpack_from('<II', data, p)
        return [offset + i * width for i in range(size)]
    strings = []
    for p in table(56, 4):
        _, start = varint(data, u32(p))
        strings.append(data[start:data.index(b'\0', start)].decode('utf8', errors='replace'))
    types = [strings[u32(p)] for p in table(64, 4)]
    protos = []
    for p in table(72, 12):
        args = u32(p + 8)
        params = [] if not args else [types[struct.unpack_from('<H', data, args + 4 + 2*i)[0]] for i in range(u32(args))]
        protos.append('(' + ''.join(params) + ')' + types[u32(p + 4)])
    methods = []
    for p in table(88, 8):
        owner, proto, name = struct.unpack_from('<HHI', data, p)
        methods.append(types[owner] + '->' + strings[name] + protos[proto])
    field_ids = []
    for p in table(80, 8):
        owner, typ, name = struct.unpack_from('<HHI', data, p)
        field_ids.append(types[owner] + '->' + strings[name] + ':' + types[typ])
    bundled = {types[u32(p)] for p in table(96, 32)}
    return set(types), bundled, set(methods), set(field_ids)

def main():
    TEMP.mkdir(parents=True, exist_ok=True)
    output = ROOT / 'core/extension-api/src/test/resources/inventory'
    output.mkdir(parents=True, exist_ok=True)
    fixture = gzip.decompress((ROOT / 'data/extension/src/test/resources/keiyoushi-index.pb').read_bytes())
    entries = [dict(fields(v)) for n, v in fields(dict(fields(fixture))[101]) if n == 1]
    summary = []
    for key in ['mangafire', 'weebcentral', 'toonily']:
        matches = [e for e in entries if e[2].decode().endswith('.' + key)]
        assert len(matches) == 1, (key, matches)
        entry = matches[0]
        apk_path = dict(fields(entry[3]))[1].decode()
        url = BASE + 'apk/' + pathlib.PurePosixPath(apk_path).name
        apk = TEMP / pathlib.PurePosixPath(apk_path).name
        raw_url = url
        provenance = apk.with_suffix('.url.txt')
        if apk.exists() and provenance.exists(): url = provenance.read_text().strip()
        if not apk.exists():
            try:
                urllib.request.urlretrieve(url, apk)
            except urllib.error.HTTPError as error:
                if error.code != 404 or not apk_path.startswith('https://'): raise
                url = apk_path
                urllib.request.urlretrieve(url, apk)
        provenance.write_text(url, encoding='utf8')
        all_types, bundled, methods, field_ids = set(), set(), set(), set()
        dumps = []
        with zipfile.ZipFile(apk) as z:
            for name in z.namelist():
                if not re.fullmatch(r'classes\d*\.dex', name): continue
                dex = TEMP / key / name
                dex.parent.mkdir(exist_ok=True)
                dex.write_bytes(z.read(name))
                dump = subprocess.check_output([str(DEXDUMP), '-f', '-d', str(dex)], text=True, encoding='utf8', errors='replace')
                dex.with_suffix('.dexdump.txt').write_text(dump, encoding='utf8')
                dumps.append(dump)
                t, b, m, f = dex_inventory(dex.read_bytes())
                all_types |= t; bundled |= b; methods |= m; field_ids |= f
        required = {t.lstrip('[') for t in all_types if t.lstrip('[').startswith('L')} - bundled
        required = {t for t in required if not t.startswith(('Ljava/', 'Ljavax/', 'Landroid/', 'Ldalvik/', 'Lorg/xml/', 'Lorg/w3c/'))}
        shim = {t for t in required if t.startswith('Leu/kanade/')}
        # Every shim type must also appear in the human-readable dexdump evidence.
        dump = '\n'.join(dumps)
        assert all(t in dump or t[1:-1] in dump for t in shim), shim
        members = {m for m in methods | field_ids if m.split('->')[0] in shim}
        external_members = {m for m in methods | field_ids if m.split('->')[0] in required}
        # Defined virtual source entry points: these reference the extension owner, not HttpSource.
        virtuals = set()
        for block in dump.split('Class #')[1:]:
            if not re.search(r"Superclass\s*:\s*'Leu/kanade/tachiyomi/source/online/HttpSource;'", block): continue
            if 'Virtual methods' not in block: continue
            virtual = block.split('Virtual methods', 1)[1]
            for name, signature in re.findall(r"name\s+: '([^']+)'\s+type\s+: '([^']+)'", virtual):
                virtuals.add('Leu/kanade/tachiyomi/source/online/HttpSource;->' + name + signature)
        for suffix, values in [('required', required), ('shim', shim), ('members', members), ('external-members', external_members), ('source-virtuals', virtuals)]:
            (output / f'{key}.{suffix}.txt').write_text('\n'.join(sorted(values)) + '\n', encoding='utf8')
        summary.append(dict(extension=key, package=entry[2].decode(), rawUrl=raw_url, fixtureUrl=apk_path, url=url, apk=str(apk), sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), required=len(required), shim=len(shim), members=len(members)))
    (output / 'apks.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf8')
    print(json.dumps(summary, indent=2))

if __name__ == '__main__': main()
