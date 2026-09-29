"""Full natural progression only. A capped observation is deliberately not a pass."""
import argparse
import re
import sys
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('log', type=Path)
args = parser.parse_args()
text = args.log.read_bytes().decode('utf-8', errors='replace')
errors = []
starts = re.findall(r'ASTRA_AUTONOMY_GROWTH START [^\r\n]+', text)
if len(starts) != 1 or 'initialResidents=6 initialBuildings=7' not in starts[0]:
    errors.append('missing unique natural starter baseline')
if 'ASTRA_AUTONOMY_GROWTH VERIFIED fullProgression=true noPlayerSupplies=true' not in text:
    errors.append('full progression was not verified')
if re.search(r'ASTRA_AUTONOMY_GROWTH (?:INCOMPLETE|FAILED)', text):
    errors.append('observation incomplete or failed')
progress = re.findall(r'ASTRA_AUTONOMY_GROWTH progress [^\r\n]+', text)
if not progress or 'civ=6 ' not in progress[-1]:
    errors.append('last observed civilization is not VI')
if progress:
    science = re.search(r'research=(\d+)/(\d+)', progress[-1])
    if not science or science[1] != science[2] or int(science[2]) == 0:
        errors.append('research catalogue incomplete')
audits = re.findall(r'ASTRA_AUTONOMY_GROWTH audit [^\r\n]+', text)
if not audits or 'absent=[]' not in audits[-1]:
    errors.append('required workplaces missing')
if errors:
    sys.exit('; '.join(errors))
print('PASSED: natural starter village completed all progression without player supplies')
