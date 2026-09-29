"""Smoke assertions in the game must pass; Gradle exit 0 alone proves only clean shutdown."""
import argparse
import json
import os
import struct
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('log', type=Path)
parser.add_argument('--mode', choices=['natural', 'reload', 'gallery', 'hall', 'hall-three', 'resource', 'research', 'research-reload', 'deep-mine', 'deep-mine-reload'], required=True)
args = parser.parse_args()
log = args.log.read_text(encoding='utf-8', errors='replace')
expected = 'reload=true' if args.mode in ('reload','hall-three','research-reload','deep-mine-reload') else 'reload=false'
assert 'ASTRA_SMOKE FAILED' not in log, 'Smoke harness reported a failure'
residents=7 if args.mode.startswith('research') else 6
if args.mode.startswith('deep-mine'):
    # A deep-mine run lasts half an hour and more: the residents eat from the hall meanwhile, so the stock may only have gone down,
    # never up (no bread made from nothing), rather than stay at the starting 41 (owner's decision, 2026-09-18).
    stock = re.search(rf'ASTRA_SMOKE VERIFIED {residents} physical NPCs; bread=(\d+); {expected}', log)
    assert stock and int(stock.group(1)) <= 41, 'Missing world/identity/stock assertion (bread must not exceed the starting 41)'
else:
    assert f'ASTRA_SMOKE VERIFIED {residents} physical NPCs; bread=41; {expected}' in log, 'Missing world/identity/stock assertion'
assert 'BUILD SUCCESSFUL' in log, 'No successful Gradle exit'
if args.mode == 'natural':
    assert 'ASTRA_NATURAL located actual structure' in log, 'No natural generation assertion'
    # AD-104: the generated farm lays its 9x9 module: 80 moist plots under wheat round one water source.
    assert 'ASTRA_FARM_FIELD VERIFIED' in log, 'No generated farm field assertion'
if args.mode in ('hall','hall-three'):
    tier=3 if args.mode == 'hall-three' else 2
    assert re.search(r'ASTRA_HALL VERIFIED builder completed [0-9]+ paid block changes; civilization='+str(tier),log), 'No paid hall completion assertion'
if args.mode.startswith('deep-mine'):
    assert 'ASTRA_DEEP_MINE VERIFIED' in log, 'No finite supported mine assertion'
if args.mode.startswith('research'):
    assert 'ASTRA_RESEARCH VERIFIED' in log, 'No physical research assertion'
if args.mode == 'resource':
    assert 'ASTRA_RESOURCE VERIFIED' in log, 'No resource work assertion'
root = Path(__file__).resolve().parents[1]
screens = []
paths = re.findall(r'ASTRA_SMOKE screenshot (.+\.png)', log)
if args.mode == 'gallery':
    assert 'ASTRA_GALLERY created 26 actual building previews' in log, 'Incomplete catalogue'
    assert len(paths) == 28, 'Expected 26 designs plus world/isometry'
    assert len({Path(p.strip()).name for p in paths if 'design-' in p}) == 26, 'Duplicate design frames'
else:
    assert len(paths) == (4 if args.mode.startswith('deep-mine') else 2), 'Unexpected captured frame count'
for captured in paths:
    path = Path(captured.strip()).resolve()
    assert path.is_relative_to(Path(os.environ.get('ASTRA_GAME_DIR') or root / 'run-client').resolve() / 'screenshots'), 'Screenshot outside test directory'
    raw = path.read_bytes()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', 'Not a PNG'
    width, height = struct.unpack('>II', raw[16:24])
    assert width >= 320 and height >= 200, 'Invalid framebuffer'
    screens.append({'file': str(path.relative_to(root)), 'width': width, 'height': height})
print(json.dumps({'status': 'PASSED', 'mode': args.mode, 'log': str(args.log), 'screenshots': screens,
                  'scope': 'Real client assertions and screenshot files; visual review is separate'}, indent=2))
