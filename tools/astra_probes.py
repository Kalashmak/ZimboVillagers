#!/usr/bin/env python3
"""Parallel client probes from one build: `python tools/astra.py probes <batch> [options] -- <probe> [then <probe>] <probe> ...`

One Gradle step under the shared build lock (compile, resources, ForgeGradle's runClient preparation with -PastraLaunchOnly, which writes
build/astra-launch/client.json and starts no game), a snapshot of the mod's classes and resources for this batch, then one client JVM per
probe started straight from that launch, each in its own game directory run-client-N with its own slot lock, log, watchdog and verifier.

A probe is one token: [name:]flag[+flag...][#verify spec]
  flag       a runClient -P flag of build.gradle (officeUiSmoke, galleryDesigns=mine@6, probeWarp=8) or a raw -Dkey=value
  #spec      the verifier, as for run --verify: "mechanics --mode office-ui" (quote the whole token in the shell)
  then       between two probes: the second runs after the first in the same game directory, only when the first passed
             (a reload of the world the first one made: farm:farmSmoke#... then farm-reload:reloadSmoke+farmSmoke#...)
Exit code 0 only when every probe passed; a probe passes on client exit 0, an ASTRA_* VERIFIED marker, no ASTRA_* FAILED marker and every
verifier PASSED (without a verifier the status is UNVERIFIED, never PASSED).
"""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
import queue
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time

import astra as A

ROOT = A.ROOT
LAUNCH = ROOT / 'build/astra-launch/client.json'
BATCH_DIR = ROOT / 'build/astra-probes'
BASE_OPTIONS = ROOT / 'run-client/options.txt'
PRINT = threading.Lock()
VERIFIED = re.compile(r'ASTRA_\w+.*\bVERIFIED\b')
FAILED = re.compile(r'ASTRA_\w+ FAILED')


# ---- build.gradle flags -> JVM system properties ---------------------------------------------
def _balanced(text: str, start: int, open_ch: str, close_ch: str) -> int:
    """Index just past the bracket that closes the one opened right before start."""
    depth, i = 1, start
    while depth:
        c = text[i]
        depth += (c == open_ch) - (c == close_ch)
        i += 1
    return i


def gradle_rules(text: str) -> list[tuple[list[str], list[tuple[str, str | None, str | None]]]]:
    """The `if(project.hasProperty(..)) { property 'k', v }` rules of runs.client: (flags, [(key, literal, flag whose value is used)])."""
    start = text.index('\n        client {')
    body = text[start:text.index('\n        server {', start)]
    rules, i = [], 0
    cond_re = re.compile(r'\bif\s*\(')
    prop_re = re.compile(r"property\s+'([^']+)'\s*,\s*(?:'([^']*)'|project\.property\('([^']+)'\))")
    while (m := cond_re.search(body, i)):
        close = _balanced(body, m.end(), '(', ')')
        cond = body[m.end():close - 1]
        k = close
        while body[k] in ' \t':
            k += 1
        if body[k] == '{':
            end = _balanced(body, k + 1, '{', '}')
            stmt, i = body[k + 1:end - 1], end
        else:
            end = body.index('\n', k)
            stmt, i = body[k:end], end
        names = re.findall(r"project\.hasProperty\('([^']+)'\)", cond)
        props = [(key, lit if ref == '' else None, ref or None) for key, lit, ref in prop_re.findall(stmt)]
        if names and props:
            rules.append((names, props))
    return rules


def system_properties(flags: list[str], rules) -> dict[str, str]:
    """What runClient -P<flag>... would set, from the same rules, plus raw -Dkey=value flags."""
    given: dict[str, str] = {}
    props: dict[str, str] = {}
    known = {n for names, _ in rules for n in names}
    for flag in flags:
        if flag.startswith('-D'):
            key, _, value = flag[2:].partition('=')
            props[key] = value or 'true'
            continue
        name, eq, value = flag.partition('=')
        if name not in known:
            raise ValueError(f'unknown probe flag {name!r}: not a -P flag of runs.client in build.gradle (use -Dkey=value for raw properties)')
        given[name] = value if eq else 'true'
    for names, rule in rules:
        if any(n in given for n in names):
            for key, literal, ref in rule:
                if ref is not None and ref not in given:
                    continue
                props[key] = given[ref] if ref is not None else literal
    return props


# ---- probe specs --------------------------------------------------------------------------------
class Probe:
    def __init__(self, token: str, batch: str):
        spec, _, self.verify = token.partition('#')
        self.verify = self.verify.strip()
        name, colon, flags = spec.partition(':')
        if not colon or '=' in name or '+' in name:
            name, flags = '', spec
        self.flags = [f for f in flags.split('+') if f]
        if not self.flags:
            raise ValueError(f'probe {token!r} has no flags')
        if not name:
            first = self.flags[0].split('=', 1)[0]
            first = re.sub(r'Smoke$', '', first)
            name = re.sub(r'(?<!^)(?=[A-Z])', '-', first).lower()
        self.name = f'{batch}-{name}'
        if not re.fullmatch(r'[\w.-]+', self.name):
            raise ValueError(f'probe name {self.name!r} may contain only letters, digits, dot, dash, underscore')
        self.props: dict[str, str] = {}
        self.result: dict = {'name': self.name, 'status': 'NOT_RUN', 'time': 0.0}


def parse_chains(tokens: list[str], batch: str) -> list[list[Probe]]:
    chains: list[list[Probe]] = []
    joined = False
    for token in tokens:
        if token == 'then':
            if not chains:
                raise ValueError("'then' needs a probe before it")
            joined = True
            continue
        probe = Probe(token, batch)
        if joined:
            chains[-1].append(probe)
        else:
            chains.append([probe])
        joined = False
    if joined:
        raise ValueError("'then' needs a probe after it")
    return chains


# ---- slots: run-client-N with their own lock ----------------------------------------------------
def take_slot(start: int) -> tuple[int, Path]:
    n = start
    while True:
        slot = ROOT / f'run-client-{n}'
        slot.mkdir(parents=True, exist_ok=True)
        lock = slot / 'astra-slot.lock'
        try:
            fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            with os.fdopen(fd, 'w', encoding='utf-8') as out:
                json.dump({'pid': os.getpid(), 'owner': os.environ.get('ASTRA_SESSION') or f'session@{os.getppid()}',
                           'started': time.strftime('%Y-%m-%d %H:%M:%S')}, out)
            return n, slot
        except FileExistsError:
            try:
                holder = json.loads(lock.read_text(encoding='utf-8'))
            except (OSError, ValueError):
                holder = {}
            if not A.pid_alive(int(holder.get('pid', 0))):
                try:
                    lock.unlink()
                except OSError:
                    pass
                continue
            n += 1


def release_slot(slot: Path) -> None:
    try:
        (slot / 'astra-slot.lock').unlink()
    except OSError:
        pass


def write_options(slot: Path, args: argparse.Namespace) -> None:
    """The owner's run-client/options.txt with the probe profile on top: no sound, no Realms notices, capped frame rate without vsync
    (lighter on the shared GPU/CPU when several clients run); view and simulation distance stay the owner's unless --render/--sim."""
    lines: list[str] = []
    for source in (BASE_OPTIONS, slot / 'options.txt'):
        if source.exists():
            lines = source.read_text(encoding='utf-8', errors='replace').splitlines()
            break
    if args.keep_options:
        if not (slot / 'options.txt').exists() and lines:
            (slot / 'options.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
        return
    profile = {'maxFps': str(args.fps), 'enableVsync': 'false', 'realmsNotifications': 'false',
               'soundCategory_master': '0.0', 'pauseOnLostFocus': 'false', 'narrator': '0', 'tutorialStep': 'none',
               'onboardAccessibility': 'false', 'skipMultiplayerWarning': 'true', 'joinedFirstServer': 'true'}
    if args.sim:
        profile['simulationDistance'] = str(args.sim)
    if args.render:
        profile['renderDistance'] = str(args.render)
    out, seen = [], set()
    for line in lines:
        key = line.split(':', 1)[0]
        if key in profile:
            out.append(f'{key}:{profile[key]}')
            seen.add(key)
        else:
            out.append(line)
    out.extend(f'{k}:{v}' for k, v in profile.items() if k not in seen)
    (slot / 'options.txt').write_text('\n'.join(out) + '\n', encoding='utf-8')


# ---- one client -----------------------------------------------------------------------------------
def argfile_line(arg: str) -> str:
    return '"' + arg.replace('\\', '\\\\').replace('"', '\\"') + '"'


def kill_tree(proc: subprocess.Popen) -> None:
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'], capture_output=True)
    else:
        proc.kill()


def diagnose(proc: subprocess.Popen, name: str, reason: str) -> list[str]:
    report = [f'WATCHDOG: {reason}; stack summary below, client stopped']
    jstack = A.LOCAL_JDK / 'bin' / ('jstack.exe' if os.name == 'nt' else 'jstack')
    try:
        dump = subprocess.run([str(jstack) if jstack.exists() else 'jstack', str(proc.pid)], capture_output=True, text=True,
                              encoding='utf-8', errors='replace', timeout=60).stdout
        full = A.DRAFT_DIR / f'{name}-stacks-{proc.pid}.txt'
        full.write_text(dump, encoding='utf-8')
        report.append(f'STACKS client pid={proc.pid} full={full.relative_to(ROOT).as_posix()} (open only if the summary is not enough)')
        report.extend(A.stack_summary(dump))
    except (OSError, subprocess.SubprocessError) as exc:
        report.append(f'  jstack {proc.pid} failed: {exc}')
    kill_tree(proc)
    try:
        proc.wait(timeout=60)
    except subprocess.TimeoutExpired:
        report.append('  client did not exit after kill')
    return report


def run_probe(probe: Probe, slot_no: int, slot: Path, launch: dict, args: argparse.Namespace) -> bool:
    directory = A.EVIDENCE_DIR if args.evidence else A.DRAFT_DIR
    directory.mkdir(parents=True, exist_ok=True)
    A.DRAFT_DIR.mkdir(parents=True, exist_ok=True)
    log = directory / f'{probe.name}.log'
    res = probe.result
    res['log'] = log.relative_to(ROOT).as_posix()
    res['slot'] = slot.name
    write_options(slot, args)
    props = dict(probe.props)
    if args.warp is not None and 'villageastra.probeWarp' not in props:
        props['villageastra.probeWarp'] = str(args.warp)
    jvm = [a for a in launch['jvmArgs']] + [f'-D{k}={v}' for k, v in sorted(props.items())]
    argv = jvm + ['-cp', os.pathsep.join(launch['classpath']), launch['mainClass'], *launch['args']]
    argfile = A.DRAFT_DIR / f'{probe.name}.args'
    argfile.write_text('\n'.join(argfile_line(a) for a in argv) + '\n', encoding='utf-8')
    java = Path(launch['javaHome'] or A.LOCAL_JDK) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    env = os.environ.copy()
    env.update(launch['environment'])
    started = time.time()
    watchdog: list[str] = []
    first_marker = None
    with log.open('w', encoding='utf-8', errors='replace') as stream:
        stream.write(f'ASTRA_PROBES client {probe.name} slot={slot.name} props={json.dumps(props, sort_keys=True)}\n')
        stream.flush()
        proc = subprocess.Popen([str(java), f'@{argfile}'], cwd=slot, env=env, stdout=stream, stderr=subprocess.STDOUT)
        last_size, last_growth, offset = -1, time.time(), 0
        while proc.poll() is None:
            time.sleep(2)
            size, now = log.stat().st_size, time.time()
            if size != last_size:
                last_size, last_growth = size, now
                if first_marker is None:
                    with log.open('r', encoding='utf-8', errors='replace') as reader:
                        reader.seek(offset)
                        chunk = reader.read()
                        offset = reader.tell()
                    if 'ASTRA_SMOKE creating' in chunk or re.search(r'ASTRA_(?!PROBES)\w+', chunk):
                        first_marker = now - started
            if args.timeout_min and now - started > args.timeout_min * 60:
                watchdog = diagnose(proc, probe.name, f'total time exceeded {args.timeout_min:g} min')
            elif args.stall_min and now - last_growth > args.stall_min * 60:
                watchdog = diagnose(proc, probe.name, f'no new log output for {args.stall_min:g} min')
            if watchdog:
                break
        code = proc.wait()
        elapsed = time.time() - started
        # The verifiers read the Gradle line; a direct client's clean exit is the same fact runClient reports with it.
        stream.write(f"\n{'BUILD SUCCESSFUL' if code == 0 and not watchdog else 'BUILD FAILED'} (direct client JVM exit {code} in {elapsed:.0f}s, no Gradle)\n")
    text = log.read_text(encoding='utf-8', errors='replace')
    lines = [f'RUN {probe.name}: exit={code} time={elapsed:.0f}s title={first_marker or 0:.0f}s log={res["log"]} lines={text.count(chr(10))}',
             f'client {" ".join(probe.flags)} (slot {slot.name}{", warp " + props["villageastra.probeWarp"] if "villageastra.probeWarp" in props else ""})']
    lines += A.digest(text) + watchdog
    verdicts = [A.verify(log, spec, slot) for spec in ([probe.verify] if probe.verify else [])]
    lines += verdicts
    markers = bool(VERIFIED.search(text)) and not FAILED.search(text)
    if watchdog:
        status = 'STALLED'
    elif code != 0 or not markers or any(not v.startswith('VERIFY PASSED') for v in verdicts):
        status = 'FAILED'
    else:
        status = 'PASSED' if verdicts else 'UNVERIFIED'
    res.update(status=status, time=elapsed, title=first_marker, exit=code, verify=verdicts)
    lines.append(f'RESULT {probe.name}: {status}')
    with PRINT:
        print('\n'.join(lines), flush=True)
    return status in ('PASSED', 'UNVERIFIED')


# ---- batch -----------------------------------------------------------------------------------------
def build(args: argparse.Namespace) -> bool:
    if not A.acquire_run_lock(f'{args.batch}-build', args.lock_wait_min):
        return False
    try:
        log = A.DRAFT_DIR / f'{args.batch}-build.log'
        A.DRAFT_DIR.mkdir(parents=True, exist_ok=True)
        env = os.environ.copy()
        if (A.LOCAL_JDK / 'bin/javac.exe').exists() or (A.LOCAL_JDK / 'bin/javac').exists():
            env['JAVA_HOME'] = str(A.LOCAL_JDK)
        wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        started = time.time()
        with log.open('w', encoding='utf-8', errors='replace') as stream:
            code = subprocess.run([str(wrapper), *args.gradle, 'runClient', '-PastraLaunchOnly'], cwd=ROOT, env=env, stdout=stream,
                                  stderr=subprocess.STDOUT).returncode
        text = log.read_text(encoding='utf-8', errors='replace')
        ok = code == 0 and 'ASTRA_LAUNCH written' in text
        print(f'BUILD {args.batch}: exit={code} time={time.time() - started:.0f}s log={log.relative_to(ROOT).as_posix()}', flush=True)
        if not ok:
            for line in A.digest(text):
                print(line)
            return False
        snapshot(args)
        return True
    finally:
        A.release_run_lock()


def snapshot(args: argparse.Namespace) -> None:
    """The batch runs from its own copy of the mod's classes and resources, so a later compile in this checkout cannot change them."""
    target = BATCH_DIR / args.batch
    if target.exists():
        shutil.rmtree(target)
    for part, source in (('classes', ROOT / 'build/classes/java/main'), ('resources', ROOT / 'build/resources/main')):
        shutil.copytree(source, target / part)


def load_launch(args: argparse.Namespace) -> dict:
    launch = json.loads(LAUNCH.read_text(encoding='utf-8'))
    target = BATCH_DIR / args.batch
    swap = {str(ROOT / 'build/classes/java/main'): str(target / 'classes'), str(ROOT / 'build/resources/main'): str(target / 'resources')}
    if target.exists():
        launch['classpath'] = [swap.get(str(Path(p)), p) for p in launch['classpath']]
        for key, value in list(launch['environment'].items()):
            for old, new in swap.items():
                value = value.replace(old, new)
            launch['environment'][key] = value
    return launch


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog='astra.py probes', description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('batch', help='Batch name; each log is <batch>-<probe>.log')
    parser.add_argument('--jobs', type=int, default=4, help='Clients at once (default 4)')
    parser.add_argument('--evidence', action='store_true', help='Logs to docs/runs instead of build/astra-runs')
    parser.add_argument('--force', action='store_true', help='Overwrite existing logs with the same names')
    parser.add_argument('--timeout-min', type=float, default=0, help='Stop a client after N minutes (default: no limit)')
    parser.add_argument('--stall-min', type=float, default=6, help='Stop a client whose log is silent for N minutes (default 6, 0 disables)')
    parser.add_argument('--lock-wait-min', type=float, default=120, help='Wait for build/astra-run.lock for the build step (default 120)')
    parser.add_argument('--warp', type=int, default=None, help='villageastra.probeWarp for every probe without its own probeWarp= flag')
    parser.add_argument('--render', type=int, default=0, help='renderDistance for the clients (default: as in run-client/options.txt; 8 measured no faster on flat or natural probe worlds and would shorten far views in frames)')
    parser.add_argument('--sim', type=int, default=0, help='simulationDistance of the probe profile (default: as in run-client/options.txt)')
    parser.add_argument('--fps', type=int, default=60, help='maxFps of the probe profile (default 60)')
    parser.add_argument('--keep-options', action='store_true', help='Use run-client/options.txt as it is (no probe profile)')
    parser.add_argument('--no-build', action='store_true', help='Reuse build/astra-launch/client.json and the current classes')
    parser.add_argument('--gradle', action='append', default=[], help='Extra Gradle task for the build step (e.g. --gradle processResources)')
    parser.add_argument('--min-free-gb', type=float, default=15, help='Refuse to start clients below this free disk space (default 15)')
    parser.add_argument('--stagger', type=float, default=5, help='Seconds between client starts (default 5)')
    parser.add_argument('--dry-run', action='store_true', help='Print each probe\'s system properties and stop')
    if '--' in argv:
        i = argv.index('--')
        opts, tokens = argv[:i], argv[i + 1:]
    else:
        opts, tokens = argv, []
    args = parser.parse_args(opts)
    if not tokens:
        parser.error('pass probes after --')
    try:
        chains = parse_chains(tokens, args.batch)
        rules = gradle_rules((ROOT / 'build.gradle').read_text(encoding='utf-8'))
        for chain in chains:
            for probe in chain:
                probe.props = system_properties(probe.flags, rules)
    except (ValueError, IndexError) as exc:
        print(f'PROBES {args.batch}: {exc}', file=sys.stderr)
        return 2
    probes = [p for chain in chains for p in chain]
    if len({p.name for p in probes}) != len(probes):
        print(f'PROBES {args.batch}: two probes share a name; give them name: prefixes', file=sys.stderr)
        return 2
    if args.dry_run:
        for chain in chains:
            print(' then '.join(f'{p.name} {json.dumps(p.props, sort_keys=True)}{" #" + p.verify if p.verify else ""}' for p in chain))
        return 0
    for p in probes:
        log = (A.EVIDENCE_DIR if args.evidence else A.DRAFT_DIR) / f'{p.name}.log'
        if log.exists() and not args.force:
            print(f'{log.relative_to(ROOT)} exists; choose a new batch name or pass --force (evidence logs are append-only)', file=sys.stderr)
            return 2
    free = shutil.disk_usage(ROOT).free / 2**30
    if free < args.min_free_gb:
        print(f'PROBES {args.batch}: only {free:.1f} GB free on the project disk, below {args.min_free_gb:g} GB; not starting '
              f'{len(probes)} clients (free space first: old run-gametest/session-* and run-client*/saves/astra-smoke-* worlds, '
              'see DEVELOPMENT.md "Хранение")', file=sys.stderr)
        return 75
    started = time.time()
    if args.no_build:
        # Reusing compilation must still isolate this client's classes. Otherwise
        # a subsequent compile can remove a lazily loaded class during startup.
        if not A.acquire_run_lock(f'{args.batch}-snapshot', args.lock_wait_min):
            return 1
        try:
            snapshot(args)
        finally:
            A.release_run_lock()
    elif not build(args):
        return 1
    build_time = time.time() - started
    launch = load_launch(args)
    work: queue.Queue = queue.Queue()
    for chain in chains:
        work.put(chain)
    jobs = max(1, min(args.jobs, len(chains)))
    slots = [take_slot(1)]
    for _ in range(jobs - 1):
        slots.append(take_slot(slots[-1][0] + 1))
    print(f'PROBES {args.batch}: {len(probes)} probes in {len(chains)} chains, {jobs} at once, slots '
          + ' '.join(s.name for _, s in slots), flush=True)

    def worker(slot_no: int, slot: Path, delay: float) -> None:
        time.sleep(delay)
        while True:
            try:
                chain = work.get_nowait()
            except queue.Empty:
                return
            for probe in chain:
                try:
                    if not run_probe(probe, slot_no, slot, launch, args):
                        break
                except Exception as exc:  # a broken launch must not hide the other probes' results
                    probe.result.update(status='FAILED', error=repr(exc))
                    with PRINT:
                        print(f'RESULT {probe.name}: FAILED ({exc!r})', flush=True)
                    break

    threads = [threading.Thread(target=worker, args=(n, s, i * args.stagger), daemon=True) for i, (n, s) in enumerate(slots)]
    try:
        for t in threads:
            t.start()
        for t in threads:
            t.join()
    finally:
        for _, s in slots:
            release_slot(s)
    wall = time.time() - started
    summed = sum(p.result['time'] for p in probes)
    print(f'PROBES {args.batch}: wall={wall:.0f}s build={build_time:.0f}s sum of client times={summed:.0f}s '
          f'({summed / max(1, wall - build_time):.1f}x parallel)')
    for p in probes:
        r = p.result
        title = f" title={r['title']:.0f}s" if r.get('title') else ''
        print(f"  {r['status']:<10} {p.name:<36} {r['time']:>6.0f}s{title} {r.get('slot', '')}")
    for line in A.prune_test_runs():
        print(line)
    (A.DRAFT_DIR / f'{args.batch}-summary.json').write_text(json.dumps(
        {'batch': args.batch, 'wall': wall, 'build': build_time, 'jobs': jobs, 'probes': [p.result for p in probes]}, indent=1), encoding='utf-8')
    return 0 if all(p.result['status'] in ('PASSED', 'UNVERIFIED') for p in probes) else 1
