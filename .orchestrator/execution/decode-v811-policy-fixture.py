"""Independent raw-wire audit against upstream index.proto (no application parser)."""
import gzip
import hashlib
import json
from pathlib import Path


def fields(data):
    pos = 0

    def varint():
        nonlocal pos
        value = shift = 0
        while True:
            byte = data[pos]
            pos += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
            shift += 7

    result = []
    while pos < len(data):
        tag = varint()
        wire = tag & 7
        if wire == 0:
            value = varint()
        elif wire == 2:
            size = varint()
            value = data[pos:pos + size]
            pos += size
        else:
            raise ValueError(f"Unexpected wire type {wire}")
        result.append((tag >> 3, wire, value))
    return result


fixture = Path("data/extension/src/test/resources/keiyoushi-index.pb")
raw = fixture.read_bytes()
index = fields(gzip.decompress(raw))
entries = fields(next(value for number, _, value in index if number == 101))
for _, _, entry in entries:
    decoded = fields(entry)
    if next(value for number, _, value in decoded if number == 2) != b"eu.kanade.tachiyomi.extension.all.mangafire":
        continue
    output = []
    for number, wire, value in decoded:
        if number in (3, 8):
            value = [{"field": n, "wire": w, "value": v.decode() if isinstance(v, bytes) else v}
                     for n, w, v in fields(value)]
        elif isinstance(value, bytes):
            value = value.decode()
        output.append({"field": number, "wire": wire, "value": value})
    report = json.dumps({"fixture": str(fixture), "sha256": hashlib.sha256(raw).hexdigest(),
                         "extensionCount": len(entries), "mangaFireRawFields": output}, indent=2)
    Path(".orchestrator/execution/V811-FIX-POLICY-raw-fields.json").write_text(report + "\n", encoding="utf-8")
    print(report)
