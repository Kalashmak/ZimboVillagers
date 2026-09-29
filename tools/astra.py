#!/usr/bin/env python3
"""Build and test helpers: run Gradle with a digest, digest logs, outline Java files.

run      Full Gradle/Minecraft output goes to a log file; stdout gets only a short digest.
digest   The same digest for an existing log.
outline  Types/methods of a Java file with line ranges, so only the needed range is read.
json     Selected part of a large JSON file (path, filters, fields) instead of reading all of it.
section  Heading list of a long document, or one section by number/text.
probes   Several client probes at once from one build, one JVM each in its own run-client-N (tools/astra_probes.py).

A digest is navigation, not proof: game results still need VERIFIED markers and verify scripts.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import json
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
LOCAL_JDK = ROOT / '.tooling/java/jdk-17.0.20.1+1'
DRAFT_DIR = ROOT / 'build/astra-runs'
EVIDENCE_DIR = ROOT / 'docs/runs'
VERIFIERS = {'smoke': 'tools/verify_smoke.py', 'mechanics': 'tools/verify_mechanics.py', 'autonomy': 'tools/verify_autonomy.py', 'first-house': 'tools/verify_first_house.py'}

PREFIX = re.compile(r'^\[\d\d:\d\d:\d\d\] \[[^\]]*/(\w+)\] \[([^\]]*)\]: ')
ASTRA_KEY = re.compile(r'ASTRA_\w+.*?(VERIFIED|FAILED|PASSED|BOUNDARY|RECOVERY|created \d+|located actual)')
JAVAC_ERROR = re.compile(r'\.java:\d+: error:')
GAMETEST_RESULT = re.compile(r'required tests? (passed|failed)|tests? failed|GAME TESTS COMPLETE|failed!|timed out', re.I)
GRADLE_TASK = re.compile(r'^> Task (\S+)( FAILED)?')
BENIGN = ('server.properties', 'RealmsClient', 'SignedJWT', 'missing mods.toml', 'Advanced terminal features',
          'NoSuchFileException: server.properties', 'Failed to load properties from file')
MAX_ASTRA, MAX_ERRORS, MAX_JAVAC = 40, 12, 20


def strip_prefix(line: str) -> str:
    return PREFIX.sub('', line).rstrip()


def junit_summary(since: float | None) -> list[str]:
    results = ROOT / 'build/test-results/test'
    files = [p for p in results.glob('TEST-*.xml') if since is None or p.stat().st_mtime >= since]
    if not files:
        return []
    total = failures = errors = skipped = 0
    failed: list[str] = []
    for p in files:
        suite = ET.parse(p).getroot()
        total += int(suite.get('tests', 0))
        failures += int(suite.get('failures', 0))
        errors += int(suite.get('errors', 0))
        skipped += int(suite.get('skipped', 0))
        for case in suite.iter('testcase'):
            bad = case.find('failure') if case.find('failure') is not None else case.find('error')
            if bad is not None:
                message = (bad.get('message') or '').splitlines()[0][:160] if bad.get('message') else ''
                failed.append(f"  FAIL {suite.get('name', '').rsplit('.', 1)[-1]}.{case.get('name')}: {message}")
    status = 'PASSED' if not failures and not errors else 'FAILED'
    return [f'JUnit {status}: {total} tests, {failures} failures, {errors} errors, {skipped} skipped'] + failed[:10]


def digest(text: str) -> list[str]:
    lines = text.splitlines()
    out: list[str] = []
    failed_tasks = [m.group(1) for l in lines if (m := GRADLE_TASK.match(l)) and m.group(2)]
    ran_tasks = [m.group(1) for l in lines if (m := GRADLE_TASK.match(l)) and m.group(1).startswith((':run', ':test', ':build', ':compile'))]
    if ran_tasks:
        out.append('Tasks: ' + ' '.join(dict.fromkeys(t.lstrip(':') for t in ran_tasks)))
    build = [l.strip() for l in lines if l.startswith(('BUILD SUCCESSFUL', 'BUILD FAILED'))]
    out.append(build[-1] if build else 'BUILD result line: none (interrupted or still running?)')
    if failed_tasks:
        out.append('Failed tasks: ' + ' '.join(failed_tasks))
    for i, l in enumerate(lines):
        if l.startswith('* What went wrong:'):
            block = []
            for x in lines[i + 1:i + 10]:
                if x.startswith('* Try'):
                    break
                if x.strip():
                    block.append(x.strip().replace(str(ROOT) + os.sep, ''))
            out.append('What went wrong: ' + ' | '.join(block)[:600])
            break
    javac = [i for i, l in enumerate(lines) if JAVAC_ERROR.search(l)]
    if javac:
        out.append(f'javac errors: {len(javac)}')
        for i in javac[:MAX_JAVAC]:
            out.append('  ' + lines[i].strip().replace(str(ROOT) + os.sep, ''))
            if i + 1 < len(lines):
                out.append('    ' + lines[i + 1].strip()[:200])
    gametest = []
    for l in lines:
        stripped = strip_prefix(l)
        if ('GameTest' in l or 'TestReporter' in l) and (GAMETEST_RESULT.search(l) or stripped.lstrip().startswith('- ')):
            gametest.append('  ' + stripped[:220])
    if gametest:
        out.append('GameTest:')
        out.extend(list(dict.fromkeys(gametest))[:15])
    astra: list[str] = []
    screenshots = 0
    for l in lines:
        if re.search(r'ASTRA_\w+ screenshot', l):
            screenshots += 1
        elif ASTRA_KEY.search(l):
            astra.append('  ' + strip_prefix(l)[:300])
    if astra or screenshots:
        unique = list(dict.fromkeys(astra))
        out.append(f'ASTRA markers ({len(unique)} key lines, {screenshots} screenshots):')
        out.extend(unique[:MAX_ASTRA])
        if len(unique) > MAX_ASTRA:
            out.append(f'  ... {len(unique) - MAX_ASTRA} more: rg -n "ASTRA_.*(VERIFIED|FAILED)" <log>')
    problems: list[str] = []
    for i, l in enumerate(lines):
        is_error = '/ERROR]' in l or '/FATAL]' in l or l.startswith(('Exception in thread', 'Caused by:'))
        if not is_error or any(b in l for b in BENIGN) or 'ASTRA_' in l:
            continue
        nxt = lines[i + 1].strip() if i + 1 < len(lines) and not lines[i + 1].lstrip().startswith('at ') else ''
        if nxt and any(b in nxt for b in BENIGN):
            continue
        problems.append('  ' + strip_prefix(l)[:260] + (f' | {nxt[:160]}' if nxt else ''))
    if problems:
        unique = list(dict.fromkeys(problems))
        out.append(f'Other ERROR/exception lines ({len(unique)} unique, benign launcher noise filtered):')
        out.extend(unique[:MAX_ERRORS])
    if not build or any('FAILED' in a for a in astra):
        recent = [strip_prefix(l)[:200] for l in lines if 'ASTRA_' in l][-5:]
        if recent:
            out.append('Last ASTRA lines (where it stopped):')
            out.extend('  ' + l for l in recent)
    if not build or (build and build[-1].startswith('BUILD FAILED') and not javac and not problems and not astra):
        out.append('Log tail:')
        out.extend('  ' + l[:200] for l in lines[-10:])
    return out


def verify(log: Path, spec: str, game_dir: Path | None = None) -> str:
    """game_dir: the client's game directory when it is not run-client (parallel probes use run-client-N)."""
    parts = shlex.split(spec)
    if not parts or parts[0] not in VERIFIERS:
        return f'VERIFY ERROR: use --verify "smoke --mode natural" or "mechanics --mode farm [--reload]", got {spec!r}'
    cmd = [sys.executable, str(ROOT / VERIFIERS[parts[0]]), str(log), *parts[1:]]
    env = os.environ.copy()
    if game_dir is not None:
        env['ASTRA_GAME_DIR'] = str(game_dir)
    res = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', errors='replace', env=env)
    if res.returncode == 0:
        return f'VERIFY PASSED: {spec}'
    reason = (res.stderr.strip().splitlines() or ['no output'])[-1]
    return f'VERIFY FAILED: {spec}: {reason[:300]}'


RUN_VALUE_OPTIONS = {'--verify', '--timeout-min', '--stall-min', '--lock-wait-min'}
RUN_FLAG_OPTIONS = {'--evidence', '--force', '-h', '--help'}


def split_run_args(tokens: list[str]) -> tuple[list[str], list[str]]:
    """astra options may appear anywhere before '--'; everything else keeps its order for Gradle."""
    if '--' in tokens:
        i = tokens.index('--')
        return tokens[:i], tokens[i + 1:]
    astra: list[str] = []
    gradle: list[str] = []
    name_seen = False
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t.split('=', 1)[0] in RUN_VALUE_OPTIONS:
            astra.append(t)
            if '=' not in t and i + 1 < len(tokens):
                astra.append(tokens[i + 1])
                i += 1
        elif t in RUN_FLAG_OPTIONS:
            astra.append(t)
        elif not name_seen and not t.startswith('-'):
            astra.append(t)
            name_seen = True
        else:
            gradle.append(t)
        i += 1
    return astra, gradle


def child_java_processes(root_pid: int) -> list[tuple[int, str]]:
    """Java descendants of our Gradle process, excluding Gradle's own JVMs. Never touches other programs."""
    rows: list[tuple[int, int, str, str]] = []
    try:
        if os.name == 'nt':
            query = ('Get-CimInstance Win32_Process | Select-Object ProcessId,ParentProcessId,Name,CommandLine'
                     ' | ConvertTo-Json -Compress')
            raw = subprocess.run(['powershell', '-NoProfile', '-Command', query], capture_output=True, text=True,
                                 encoding='utf-8', errors='replace', timeout=60).stdout
            data = json.loads(raw or '[]')
            for d in data if isinstance(data, list) else [data]:
                rows.append((d['ProcessId'], d['ParentProcessId'], d.get('Name') or '', d.get('CommandLine') or ''))
        else:
            raw = subprocess.run(['ps', '-eo', 'pid=,ppid=,comm=,args='], capture_output=True, text=True, timeout=60).stdout
            for line in raw.splitlines():
                parts = line.split(None, 3)
                if len(parts) >= 3:
                    rows.append((int(parts[0]), int(parts[1]), parts[2], parts[3] if len(parts) > 3 else ''))
    except (OSError, ValueError, KeyError, subprocess.SubprocessError):
        return []
    return java_descendants(rows, root_pid)


def java_descendants(rows: list[tuple[int, int, str, str]], root_pid: int) -> list[tuple[int, str]]:
    children: dict[int, list[tuple[int, str, str]]] = {}
    for pid, ppid, name, cmdline in rows:
        children.setdefault(ppid, []).append((pid, name, cmdline))
    found: list[tuple[int, str]] = []
    queue, seen = [root_pid], {root_pid}
    while queue:
        for pid, name, cmdline in children.get(queue.pop(), []):
            if pid in seen:
                continue
            seen.add(pid)
            queue.append(pid)
            gradle_jvm = any(x in cmdline for x in ('GradleDaemon', 'GradleWrapperMain', 'gradle-wrapper.jar', 'GradleWorkerMain'))
            if name.lower().startswith('java') and not gradle_jvm:
                low = cmdline.lower()
                found.append((pid, 'gametest' if 'gametest' in low else 'client' if 'client' in low else 'java'))
    return found


def stack_summary(dump: str, max_frames: int = 10) -> list[str]:
    """Render/Server threads and any thread inside mod code: state, top frames, mod frames, lock lines."""
    out: list[str] = []
    for block in re.split(r'\n\s*\n', dump):
        block = block.strip()
        if not block.startswith('"'):
            continue
        lines = block.splitlines()
        name = lines[0].split('"')[1]
        frames = [l.strip() for l in lines if l.strip().startswith('at ')]
        mod = [f for f in frames if 'villageastra' in f]
        if name not in ('Render thread', 'Server thread') and not mod:
            continue
        state = next((l.split(':', 1)[1].strip() for l in lines if 'java.lang.Thread.State:' in l), '?')
        keep = frames[:3] + [f for f in mod if f not in frames[:3]]
        locks = [l.strip() for l in lines if l.strip().startswith(('- waiting', '- parking', '- blocked'))][:2]
        out.append(f'  "{name}" {state}')
        out.extend('    ' + f[:180] for f in keep[:max_frames] + locks)
    return out


def diagnose_and_stop(proc: subprocess.Popen, name: str, reason: str) -> list[str]:
    report = [f'WATCHDOG: {reason}; stack summary below, process tree stopped']
    jstack = LOCAL_JDK / 'bin' / ('jstack.exe' if os.name == 'nt' else 'jstack')
    for pid, kind in child_java_processes(proc.pid):
        try:
            dump = subprocess.run([str(jstack) if jstack.exists() else 'jstack', str(pid)], capture_output=True,
                                  text=True, encoding='utf-8', errors='replace', timeout=60).stdout
        except (OSError, subprocess.SubprocessError) as exc:
            report.append(f'  jstack {pid} failed: {exc}')
            continue
        full = DRAFT_DIR / f'{name}-stacks-{pid}.txt'
        full.write_text(dump, encoding='utf-8')
        report.append(f'STACKS {kind} pid={pid} full={full.relative_to(ROOT).as_posix()} (open only if the summary is not enough)')
        report.extend(stack_summary(dump))
    if os.name == 'nt':
        subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'], capture_output=True)
    else:
        proc.kill()
    try:
        proc.wait(timeout=60)
    except subprocess.TimeoutExpired:
        report.append('  process did not exit after kill')
    return report


RUN_LOCK = ROOT / 'build/astra-run.lock'


def pid_alive(pid: int) -> bool:
    """Whether a process still runs. On Windows os.kill would terminate it, so the process table is asked instead."""
    if pid <= 0:
        return False
    if os.name == 'nt':
        import ctypes
        handle = ctypes.windll.kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
        if not handle:
            return False
        code = ctypes.c_ulong()
        ok = ctypes.windll.kernel32.GetExitCodeProcess(handle, ctypes.byref(code))
        ctypes.windll.kernel32.CloseHandle(handle)
        return bool(ok) and code.value == 259  # STILL_ACTIVE
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def lock_holder() -> dict | None:
    try:
        return json.loads(RUN_LOCK.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return None


def acquire_run_lock(name: str, wait_min: float) -> bool:
    """One Gradle/Minecraft run at a time for every session working in this project: the next one waits, a dead holder is taken over."""
    RUN_LOCK.parent.mkdir(parents=True, exist_ok=True)
    me = {'owner': os.environ.get('ASTRA_SESSION') or f'session@{os.getppid()}', 'run': name, 'pid': os.getpid(),
          'started': time.strftime('%Y-%m-%d %H:%M:%S')}
    deadline, told = time.time() + wait_min * 60, None
    while True:
        try:
            fd = os.open(RUN_LOCK, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
            me['started'] = time.strftime('%Y-%m-%d %H:%M:%S')
            with os.fdopen(fd, 'w', encoding='utf-8') as out:
                json.dump(me, out)
            return True
        except FileExistsError:
            holder = lock_holder()
            if holder is None or not pid_alive(int(holder.get('pid', 0))):
                try:
                    RUN_LOCK.unlink()
                except OSError:
                    pass
                continue
            key = (holder.get('owner'), holder.get('run'))
            if key != told:
                print(f"RUN {name}: waiting for {holder.get('owner')} / {holder.get('run')} (since {holder.get('started')})", flush=True)
                told = key
            if time.time() > deadline:
                print(f'RUN {name}: gave up waiting after {wait_min:g} min', file=sys.stderr)
                return False
            time.sleep(15)


def release_run_lock() -> None:
    holder = lock_holder()
    if holder and int(holder.get('pid', 0)) == os.getpid():
        try:
            RUN_LOCK.unlink()
        except OSError:
            pass


def cmd_lock(args: argparse.Namespace) -> int:
    holder = lock_holder()
    if holder is None:
        print('LOCK free')
    elif not pid_alive(int(holder.get('pid', 0))):
        print(f"LOCK stale: {holder.get('owner')} / {holder.get('run')} (pid {holder.get('pid')} is gone; the next run takes it over)")
    else:
        print(f"LOCK held by {holder.get('owner')} / {holder.get('run')} since {holder.get('started')} (pid {holder.get('pid')})")
    return 0


def cmd_run(args: argparse.Namespace) -> int:
    if not args.gradle:
        print('Nothing to run: pass Gradle tasks after --', file=sys.stderr)
        return 2
    if not acquire_run_lock(args.name, args.lock_wait_min):
        return 75
    try:
        return run_locked(args)
    finally:
        release_run_lock()
        for line in prune_test_runs():
            print(line)


# ---- retention of test worlds and GameTest sessions (owner decision of 2026-09-22: newest 10 of each) ------------
SMOKE_WORLD = re.compile(r'astra-smoke-\d+')


def _size(path: Path) -> int:
    total = 0
    for dirpath, _, files in os.walk(path):
        for f in files:
            try:
                total += os.path.getsize(os.path.join(dirpath, f))
            except OSError:
                pass
    return total


def _slot_busy(game_dir: Path) -> bool:
    """A run-client-N a live probe batch of another process holds (its astra-slot.lock)."""
    try:
        holder = json.loads((game_dir / 'astra-slot.lock').read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return False
    pid = int(holder.get('pid', 0))
    return pid != os.getpid() and pid_alive(pid)


def prune_test_runs(keep: int | None = None) -> list[str]:
    """Beyond the newest `keep` (ASTRA_KEEP, default 10) run-gametest/session-* and, in every run-client*/saves, astra-smoke-* worlds.
    Reports only, unless the owner sets ASTRA_PRUNE=1 in his environment: then those old ones are removed after each run/probes batch.
    Never touched: anything else in saves, the world named in astra-smoke-world.txt (the next reload opens it), worlds named in STATE.md,
    anything changed in the last 10 minutes, and the directories of a run or probe batch another live process holds."""
    keep = int(os.environ.get('ASTRA_KEEP', '10')) if keep is None else keep
    apply = os.environ.get('ASTRA_PRUNE') == '1'
    if keep <= 0:
        return []
    holder = lock_holder()
    other_run = bool(holder) and int(holder.get('pid', 0)) != os.getpid() and pid_alive(int(holder.get('pid', 0)))
    try:
        protected = set(SMOKE_WORLD.findall((ROOT / 'STATE.md').read_text(encoding='utf-8')))
    except OSError:
        protected = set()
    groups: list[tuple[str, list[Path], set[str]]] = []
    if not other_run and (ROOT / 'run-gametest').is_dir():
        groups.append(('sessions', [p for p in (ROOT / 'run-gametest').glob('session-*') if p.is_dir()], set()))
    for game_dir in sorted(p for p in ROOT.glob('run-client*') if p.is_dir()):
        if _slot_busy(game_dir) or (other_run and game_dir.name == 'run-client'):
            continue
        last = set()
        try:
            last.add((game_dir / 'astra-smoke-world.txt').read_text(encoding='utf-8').strip())
        except OSError:
            pass
        worlds = [p for p in (game_dir / 'saves').glob('astra-smoke-*') if p.is_dir() and SMOKE_WORLD.fullmatch(p.name)]
        groups.append(('worlds', worlds, last | protected))
    removed = {'sessions': 0, 'worlds': 0}
    freed, failed, now = 0, 0, time.time()
    for kind, dirs, keep_names in groups:
        dirs.sort(key=lambda p: p.stat().st_mtime, reverse=True)
        for old in dirs[keep:]:
            if old.name in keep_names or now - old.stat().st_mtime < 600:
                continue
            size = _size(old)
            if not apply:
                removed[kind] += 1
                freed += size
                continue
            try:
                import shutil
                shutil.rmtree(old)
                removed[kind] += 1
                freed += size
            except OSError:
                failed += 1
    if not removed['sessions'] and not removed['worlds'] and not failed:
        return []
    note = f', {failed} could not be removed (in use?)' if failed else ''
    if not apply:
        return [f"RETENTION beyond the newest {keep}: {removed['worlds']} astra-smoke worlds and {removed['sessions']} GameTest sessions, "
                f'{freed / 2**30:.2f} GB (report only; the owner turns removal on with ASTRA_PRUNE=1)']
    return [f"PRUNE newest {keep} kept: removed {removed['worlds']} astra-smoke worlds and {removed['sessions']} GameTest sessions, "
            f'freed {freed / 2**30:.2f} GB{note} (ASTRA_PRUNE unset turns removal off)']


def gametest_failure(text: str, tasks: list[str]) -> str | None:
    """A successful Gradle process is not evidence that a filtered suite ran."""
    if not any(task.split(':')[-1] == 'runGameTestServer' for task in tasks):
        return None
    totals = re.findall(r'\b(\d+) GAME TESTS COMPLETE\b', text)
    if not totals:
        return 'GameTest NOT_RUN: missing completed-suite marker'
    if any(int(total) == 0 for total in totals):
        return 'GameTest NOT_RUN: empty selection; check -PgtOnly (test names or batches, not class names)'
    if re.search(r'\b[1-9]\d* required tests? failed\b', text, re.I):
        return 'GameTest FAILED: required tests failed'
    if not re.search(r'\bAll \d+ required tests passed\b', text):
        return 'GameTest NOT_RUN: missing required-tests result'
    return None


def run_locked(args: argparse.Namespace) -> int:
    gradle_args = args.gradle
    if not gradle_args:
        print('Nothing to run: pass Gradle tasks after --', file=sys.stderr)
        return 2
    if not re.fullmatch(r'[\w.-]+', args.name):
        print('Run name may contain only letters, digits, dot, dash, underscore', file=sys.stderr)
        return 2
    directory = EVIDENCE_DIR if args.evidence else DRAFT_DIR
    directory.mkdir(parents=True, exist_ok=True)
    DRAFT_DIR.mkdir(parents=True, exist_ok=True)
    log = directory / f'{args.name}.log'
    if log.exists() and not args.force:
        print(f'{log.relative_to(ROOT)} exists; choose a new name or pass --force (evidence logs are append-only)', file=sys.stderr)
        return 2
    runs_game = any(a.split(':')[-1] in ('runClient', 'runServer', 'runGameTestServer') for a in gradle_args)
    stall_min = args.stall_min if args.stall_min is not None else (6.0 if runs_game else 0.0)
    env = os.environ.copy()
    if (LOCAL_JDK / 'bin/javac.exe').exists() or (LOCAL_JDK / 'bin/javac').exists():
        env['JAVA_HOME'] = str(LOCAL_JDK)
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    started = time.time()
    watchdog: list[str] = []
    with log.open('w', encoding='utf-8', errors='replace') as stream:
        proc = subprocess.Popen([str(wrapper), *gradle_args], cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
        last_size, last_growth = -1, time.time()
        while proc.poll() is None:
            time.sleep(2)
            size, now = log.stat().st_size, time.time()
            if size != last_size:
                last_size, last_growth = size, now
            if args.timeout_min and now - started > args.timeout_min * 60:
                watchdog = diagnose_and_stop(proc, args.name, f'total time exceeded {args.timeout_min:g} min')
            elif stall_min and now - last_growth > stall_min * 60:
                watchdog = diagnose_and_stop(proc, args.name, f'no new log output for {stall_min:g} min')
            if watchdog:
                break
        code = proc.returncode
    elapsed = time.time() - started
    text = log.read_text(encoding='utf-8', errors='replace')
    print(f'RUN {args.name}: exit={code} time={elapsed:.0f}s log={log.relative_to(ROOT).as_posix()} lines={text.count(chr(10))}')
    print('gradle ' + ' '.join(gradle_args))
    for line in digest(text):
        print(line)
    for line in watchdog:
        print(line)
    if any(a == 'test' or a.endswith(':test') for a in gradle_args):
        for line in junit_summary(started - 1) or ['JUnit: no fresh results (task up-to-date or not executed)']:
            print(line)
    game_failure = gametest_failure(text, gradle_args)
    if game_failure:
        print(game_failure)
    failed_verify = game_failure is not None
    for spec in args.verify:
        result = verify(log, spec)
        failed_verify |= not result.startswith('VERIFY PASSED')
        print(result)
    if watchdog:
        return 124
    return code if code else (1 if failed_verify else 0)


def cmd_digest(args: argparse.Namespace) -> int:
    text = args.log.read_text(encoding='utf-8', errors='replace')
    print(f'DIGEST {args.log}: lines={text.count(chr(10))}')
    for line in digest(text):
        print(line)
    for spec in args.verify:
        print(verify(args.log.resolve(), spec))
    return 0


# ---- Java outline ---------------------------------------------------------------------------
TYPE_DECL = re.compile(r'\b(class|interface|enum|record)\s+([A-Za-z_$][\w$]*)')
METHOD_DECL = re.compile(r'([A-Za-z_$][\w$]*)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)\s*(?:throws\s+[\w.,\s]+)?$')
NOT_METHODS = {'if', 'for', 'while', 'switch', 'catch', 'synchronized', 'return', 'new', 'else', 'try', 'do'}


def outline(text: str) -> list[tuple[int, int, int, str]]:
    """(depth, start_line, end_line, label) for types and methods. Heuristic scanner, not a parser."""
    rows: list[list] = []
    stack: list[int | None] = []  # index into rows for each open brace, or None
    header: list[str] = []
    header_line = 0
    line = 1
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '\n':
            line += 1
            header.append(' ')
            i += 1
            continue
        if text.startswith('//', i):
            j = text.find('\n', i)
            i = n if j < 0 else j
            continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2)
            j = n if j < 0 else j + 2
            line += text.count('\n', i, j)
            i = j
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            line += text.count('\n', i, j)
            header.append('""')
            i = j
            continue
        if c in '"\'':
            j = i + 1
            while j < n and text[j] != c and text[j] != '\n':
                j += 2 if text[j] == '\\' else 1
            header.append('""')
            i = j + 1
            continue
        if c == '{':
            h = re.sub(r'@[\w.]+(\([^()]*(\([^()]*\)[^()]*)*\))?', ' ', ''.join(header))  # drop annotations
            h = ' '.join(h.split())
            index = None
            if not stack or (stack[-1] is not None and rows[stack[-1]][4] == 'type'):
                t = TYPE_DECL.search(h)
                m = METHOD_DECL.search(h)
                if t and '=' not in h.split(t.group(0))[0] and '(' not in h.split(t.group(0))[0]:
                    rows.append([len(stack), header_line or line, line, f'{t.group(1)} {t.group(2)}', 'type'])
                    index = len(rows) - 1
                elif m and m.group(1) not in NOT_METHODS and '=' not in h and '->' not in h:
                    params = ' '.join(m.group(2).split())
                    rows.append([len(stack), header_line or line, line, f'{m.group(1)}({params[:90]})', 'method'])
                    index = len(rows) - 1
            stack.append(index)
            header, header_line = [], 0
            i += 1
            continue
        if c == '}':
            if stack:
                index = stack.pop()
                if index is not None:
                    rows[index][2] = line
            header, header_line = [], 0
            i += 1
            continue
        if c == ';':
            header, header_line = [], 0
            i += 1
            continue
        if not c.isspace() and not header_line:
            header_line = line
        header.append(c)
        i += 1
    return [(r[0], r[1], r[2], r[3]) for r in rows]


def cmd_outline(args: argparse.Namespace) -> int:
    for path in args.files:
        text = path.read_text(encoding='utf-8', errors='replace')
        rows = outline(text)
        if args.grep:
            rows = [r for r in rows if args.grep.casefold() in r[3].casefold()]
        print(f'{path.as_posix()}: {text.count(chr(10)) + 1} lines, {len(rows)} members')
        for depth, start, end, label in rows:
            print(f"{'  ' * max(depth, 0)}L{start}-{end} {label}")
    return 0


# ---- Document sections ---------------------------------------------------------------------
MD_HEADING = re.compile(r'^(#{1,6})\s+(.+)$')
NUMBERED_HEADING = re.compile(r'^((?:\d+\.)+)\s+(.+)$')


def headings(lines: list[str]) -> list[tuple[int, int, str]]:
    """(line_index, level, text): markdown '#' headings and numbered ALL-CAPS headings like '7.4. ПОЛЕВОЕ'."""
    found = []
    for i, line in enumerate(lines):
        if m := MD_HEADING.match(line):
            found.append((i, len(m.group(1)), line.strip()))
        elif (m := NUMBERED_HEADING.match(line.strip())) and not any(c.islower() for c in m.group(2)) \
                and sum(c.isalpha() for c in m.group(2)) >= 4:
            found.append((i, m.group(1).count('.'), line.strip()))
    return found


def section(text: str, query: str) -> list[str] | None:
    lines = text.splitlines()
    marks = headings(lines)
    q = query.casefold()
    for n, (i, level, title) in enumerate(marks):
        numbered = NUMBERED_HEADING.match(title)
        if (numbered and numbered.group(1).rstrip('.') == query.rstrip('.')) or q in title.casefold():
            end = next((j for j, lvl, _ in marks[n + 1:] if lvl <= level), len(lines))
            return [f'L{i + 1}-{end}'] + lines[i:end]
    return None


def cmd_section(args: argparse.Namespace) -> int:
    text = args.file.read_text(encoding='utf-8')
    if not args.query:
        for i, level, title in headings(text.splitlines()):
            print(f"L{i + 1} {'  ' * (level - 1)}{title[:120]}")
        return 0
    result = section(text, args.query)
    if result is None:
        print(f'No heading matches {args.query!r}; run without a query for the list', file=sys.stderr)
        return 2
    print('\n'.join(result))
    return 0


# ---- JSON query ----------------------------------------------------------------------------
def dig(value, dotted: str):
    """Dotted key lookup (list indexes allowed); None when absent."""
    for part in [p for p in dotted.split('.') if p]:
        try:
            value = value[int(part)] if isinstance(value, list) else value[part]
        except (KeyError, IndexError, ValueError, TypeError):
            return None
    return value


def json_select(value, path: str = '', where: list[str] | None = None, fields: str = ''):
    """Dotted path; list items filtered by key=value or key~substring (keys may be dotted, e.g. title.ru_ru)."""
    for part in [p for p in path.split('.') if p]:
        value = value[int(part)] if isinstance(value, list) else value[part]
    if isinstance(value, list):
        for cond in where or []:
            tilde, equal = cond.find('~'), cond.find('=')
            if tilde > 0 and (equal < 0 or tilde < equal):
                key, needle = cond.split('~', 1)
                value = [v for v in value if needle.casefold() in json.dumps(dig(v, key), ensure_ascii=False).casefold()]
            else:
                key, expected = cond.split('=', 1)
                value = [v for v in value if str(dig(v, key)) == expected]
        if fields:
            keys = fields.split(',')
            value = [{k: dig(v, k) for k in keys} for v in value]
    return value


def shape(value) -> str:
    if isinstance(value, dict):
        return 'object{' + ', '.join(f'{k}:{type(v).__name__}' + (f'[{len(v)}]' if isinstance(v, (list, dict)) else '')
                                      for k, v in list(value.items())[:40]) + '}'
    if isinstance(value, list):
        return f'list[{len(value)}]' + (' of ' + shape(value[0]) if value else '')
    return type(value).__name__


def cmd_json(args: argparse.Namespace) -> int:
    value = json_select(json.loads(args.file.read_text(encoding='utf-8')), args.path, args.where, args.fields)
    if args.shape or (isinstance(value, dict) and not args.path):
        print(shape(value))
    elif isinstance(value, list):
        print(f'matches={len(value)}')
        for item in value[:args.limit]:
            print(json.dumps(item, ensure_ascii=False, separators=(',', ':')))
        if len(value) > args.limit:
            print(f'... {len(value) - args.limit} more (raise --limit or narrow --where)')
    else:
        print(json.dumps(value, ensure_ascii=False, separators=(',', ':')))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='command', required=True)
    run = sub.add_parser('run', help='Run gradlew with local JDK; full log to file, digest to stdout')
    run.add_argument('name', help='Log name, e.g. farm-client-02')
    run.add_argument('--evidence', action='store_true', help='Write docs/runs/NAME.log instead of build/astra-runs/NAME.log')
    run.add_argument('--force', action='store_true', help='Overwrite an existing log with the same name')
    run.add_argument('--verify', action='append', default=[], metavar='SPEC',
                     help='"smoke --mode natural" or "mechanics --mode farm --reload"; repeatable')
    run.add_argument('--timeout-min', type=float, default=0, help='Stop after N minutes in total (default: no limit)')
    run.add_argument('--stall-min', type=float, default=None,
                     help='Stop when the log is silent for N minutes; default 6 for run* tasks, 0 disables')
    run.add_argument('--lock-wait-min', type=float, default=120,
                     help='How long to wait while another session holds build/astra-run.lock (default 120; exit 75 when it runs out)')
    run.epilog = ('Gradle tasks and -P flags go after --. Watchdog stop prints a stack summary and exits 124. '
                  'Only one run at a time across sessions: build/astra-run.lock; set ASTRA_SESSION to name yourself in it.')
    lock = sub.add_parser('lock', help='Who holds the shared run lock now')
    dig = sub.add_parser('digest', help='Short summary of an existing Gradle/Minecraft log')
    dig.add_argument('log', type=Path)
    dig.add_argument('--verify', action='append', default=[], metavar='SPEC')
    out = sub.add_parser('outline', help='Types and methods with line ranges')
    out.add_argument('files', type=Path, nargs='+')
    out.add_argument('--grep', default='', help='Only members whose label contains this text')
    js = sub.add_parser('json', help='Query part of a large JSON file instead of reading it')
    js.add_argument('file', type=Path)
    js.add_argument('--path', default='', help='Dotted path, e.g. nodes or catalog.3')
    js.add_argument('--where', action='append', default=[], help='key=value or key~substring; repeatable')
    js.add_argument('--fields', default='', help='Comma-separated keys to keep')
    js.add_argument('--limit', type=int, default=20)
    js.add_argument('--shape', action='store_true', help='Structure only, no values')
    sec = sub.add_parser('section', help='List headings of a long document, or print one section')
    sec.add_argument('file', type=Path)
    sec.add_argument('query', nargs='?', default='', help='Section number (7.4) or heading text; omit for the list')
    argv = sys.argv[1:] if argv is None else argv
    if argv and argv[0] == 'probes':
        sys.path.insert(0, str(Path(__file__).resolve().parent))
        import astra_probes
        return astra_probes.main(argv[1:])
    if argv and argv[0] == 'run':
        astra_args, gradle_args = split_run_args(argv[1:])
        args = parser.parse_args(['run', *astra_args])
        args.gradle = gradle_args
    else:
        args = parser.parse_args(argv)
    commands = {'run': cmd_run, 'digest': cmd_digest, 'outline': cmd_outline, 'json': cmd_json, 'section': cmd_section, 'lock': cmd_lock}
    return commands[args.command](args)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
    raise SystemExit(main())
