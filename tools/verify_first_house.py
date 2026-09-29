"""A partial material delivery is not a completed natural house."""
import argparse
import re
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('log', type=Path)
args = parser.parse_args()
text = args.log.read_bytes().decode('utf-8', errors='replace')
starts = re.findall(r'ASTRA_FIRST_HOUSE START [^\r\n]+', text)
errors = []
if len(starts) != 1 or 'initialResidents=6 initialBuildings=7 observerOnly=true' not in starts[0]:
    errors.append('missing unique natural starter baseline')
if 'ASTRA_FIRST_HOUSE VERIFIED newHome=true geometry=true noPlayerSupplies=true' not in text:
    errors.append('first new house not verified')
if re.search(r'ASTRA_FIRST_HOUSE (?:FAILED|INCOMPLETE)', text):
    errors.append('observation incomplete or failed')
if errors:
    sys.exit('FIRST HOUSE FAILED: ' + '; '.join(errors))
print('FIRST HOUSE PASSED')
