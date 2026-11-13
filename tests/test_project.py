import os
import pathlib
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
JAVA = os.environ.get('JAVA', 'java')
GEN = ROOT / 'build' / 'levelgen'
CLASSES = ROOT / 'build' / 'classes'

def run(*args, ok=True):
    p = subprocess.run([str(x) for x in args], capture_output=True, text=True)
    if (p.returncode == 0) != ok:
        raise AssertionError(f'{args}: {p.returncode}\n{p.stdout}\n{p.stderr}')
    return p

def check(path, ok=True):
    return run(JAVA, '-Djava.awt.headless=true', '-cp', CLASSES, 'MazeGame', '--check', path, ok=ok)

with tempfile.TemporaryDirectory() as tmp:
    tmp = pathlib.Path(tmp)
    a, b = tmp / 'a.map', tmp / 'b.map'
    for seed in [0, 1, 42, 123456, 4294967295]:
        run(GEN, a, seed)
        check(a)
        run(GEN, b, seed)
        assert a.read_bytes() == b.read_bytes(), 'Generation must be repeatable'
    run(GEN, a, -1, ok=False)
    run(GEN, a, 4294967296, ok=False)
    run(GEN, tmp / 'missing' / 'map', 42, ok=False)
    check(ROOT / 'maps' / 'default.map')
    invalid = [
        '#####\n#SCE#\n####\n',              # non-rectangular
        '#####\n#SCE.\n#####\n',             # open border
        '#####\n#S?E#\n#C..#\n#####\n',       # unknown character
        '#######\n#S#C.E#\n#######\n',       # unreachable objectives
        '#####\n#S.E#\n#####\n',             # no crystals
        '######\n#S.CE#\n#S...#\n######\n',   # duplicate start
    ]
    for data in invalid:
        a.write_text(data, encoding='utf-8')
        check(a, ok=False)
    check(tmp / 'not-found.map', ok=False)
print('Generator and map validation: OK')
