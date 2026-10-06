"""Summarize natural-growth evidence without treating a partial observation as a pass."""
import argparse
import hashlib
import json
import re
from pathlib import Path


def analyze(log, root):
    text = log.read_text(encoding='utf-8', errors='replace')
    observations = []
    for line in text.splitlines():
        if 'ASTRA_AUTONOMY_GROWTH progress ' not in line:
            continue
        match = re.search(r'active=(\d+) civ=(\d+).*?research=(\d+)/(\d+) selected=(\S*) project=(\S*) funded=(true|false) complete=(true|false) ops=(\d+)/(\d+)', line)
        if match:
            tick, civ, done, total, selected, project, funded, complete, ops, planned = match.groups()
            observations.append(dict(active_ticks=int(tick), civilization=int(civ), research_done=int(done),
                                     research_total=int(total), selected=selected, project=project,
                                     funded=funded == 'true', complete=complete == 'true',
                                     operations_done=int(ops), operations_total=int(planned), evidence=line))
    science = json.loads((root / 'src/main/resources/data/villageastra/balance/science.json').read_text(encoding='utf-8'))
    tree = json.loads((root / 'src/main/resources/data/villageastra/catalog/research_tree.json').read_text(encoding='utf-8'))
    works = sum(science['works_per_tier'] * (node['tier'] - 1) for node in tree['nodes'])
    # One laboratory counts; assume its maximum seats occupied from the very first tick.
    minimum = (works * science['work_ticks'] + max(science['seats']) - 1) // max(science['seats'])
    verified = 'ASTRA_AUTONOMY_GROWTH VERIFIED fullProgression=true noPlayerSupplies=true' in text
    failures = re.findall(r'ASTRA_AUTONOMY_GROWTH (?:FAILED|INCOMPLETE) [^\r\n]+', text)
    return dict(log=str(log), log_sha256=hashlib.sha256(log.read_bytes()).hexdigest(),
                full_progression_marker=verified, failures=failures, observations=observations,
                milestones=re.findall(r'ASTRA_AUTONOMY_GROWTH milestone=([^\r\n]+)', text),
                science_bound=dict(works=works, maximum_scientists=max(science['seats']),
                                   minimum_ticks=minimum, minimum_hours_at_20_tps=minimum / 72000,
                                   note='Optimistic research-only lower bound from current source; startup, building and staff growth add time.'),
                note='Only verify_autonomy.py supplies the acceptance verdict. Unfunded projects can gain materials while operations remain zero.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = analyze(args.log, Path(__file__).resolve().parents[1])
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
    if args.output:
        if args.output.exists():
            parser.error('output already exists; choose a new evidence file')
        args.output.write_text(rendered, encoding='utf-8')
    else:
        print(rendered, end='')
