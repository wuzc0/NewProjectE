import json
from pathlib import Path
import subprocess
import zipfile
import sys
import os
import shutil

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parent
JAVA = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else ROOT / 'jdk-25/bin'
def get_classpath():
    paths = [ROOT / 'ported-base.jar', ROOT / 'deps/minecraft-client.jar', ROOT / 'deps/minecraft-server.jar']
    paths += list((ROOT / 'deps').glob('*.jar')) + list((ROOT / 'deps/libraries').glob('*.jar'))
    return [path for path in dict.fromkeys(paths) if 'bundle' not in path.name]


CLASSPATH = get_classpath()


def compile_sources():
    (ROOT / 'classes').mkdir(exist_ok=True)
    arguments = ['-encoding', 'UTF-8', '-proc:none', '-implicit:none', '-Xmaxerrs', '200', '-cp', ';'.join(map(str, get_classpath())), '-d', str(ROOT / 'classes')]
    arguments.extend(str(path) for path in (ROOT / 'src').rglob('*.java'))
    (ROOT / 'javac.args').write_text('\n'.join('"' + argument.replace('\\', '/') + '"' for argument in arguments))
    result = subprocess.run([str(JAVA / 'javac.exe'), '-J-Duser.language=en', '@' + str(ROOT / 'javac.args')], capture_output=True, text=True, encoding='utf-8', errors='replace')
    (ROOT / 'compile.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    print(result.stdout + result.stderr)
    return result.returncode


if __name__ == '__main__':
    raise SystemExit(compile_sources())
