"""Verify physical terminal upgrades with finite supplied fixture stock; not fresh-world autonomy."""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('log', type=Path)
parser.add_argument('--types', help='Comma-separated subset; omit to require every supported terminal building')
args = parser.parse_args()
levels = json.loads((ROOT / 'src/main/resources/data/villageastra/balance/levels.json').read_text(encoding='utf-8'))
catalog = json.loads((ROOT / 'src/main/resources/data/villageastra/catalog/building_progression.json').read_text(encoding='utf-8'))
maxima = {b['building_id']: b.get('max_level', 6) for b in catalog['catalog']}
# Bakery is the legacy alias of restaurant, absent from the orderable catalogue.
expected = set(args.types.split(',')) if args.types else set(levels['catalog']) - {'bakery'}
text = args.log.read_bytes().decode('utf-8', errors='replace')
rows = re.findall(r'ZIMBO_TERMINAL_BUILD VERIFIED type=(\w+) tier=(\d+) ticks=(\d+) physicalFunding=true geometry=true fixtureStock=true', text)
passed = {}
errors = []
for building, tier, ticks in rows:
    if building in passed:
        errors.append('duplicate success: ' + building)
    passed[building] = int(tier)
    if building not in levels['catalog'] or int(tier) != maxima[levels['catalog'][building]] or int(ticks) <= 0:
        errors.append('wrong terminal tier or duration: ' + building)
if expected - passed.keys():
    errors.append('missing physical terminal upgrades: ' + ', '.join(sorted(expected - passed.keys())))
if re.search(r'ZIMBO_TERMINAL_BUILD STALLED|failed!|BUILD FAILED', text):
    errors.append('failed or stalled run')
if not re.search(r'All \d+ required tests passed', text) or 'BUILD SUCCESSFUL' not in text:
    errors.append('server and build did not finish successfully')
if errors:
    sys.exit('; '.join(errors))
print(f'PASSED: {len(expected)} physical terminal upgrades; finite fixture stock, not natural growth')
