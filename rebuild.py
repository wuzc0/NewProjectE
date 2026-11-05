import argparse
import subprocess
import sys
import build
from package import package

parser = argparse.ArgumentParser(description="Build the ProjectE Fabric release.")
parser.add_argument('--offline', action='store_true', help='Reuse dependencies already prepared in deps/.')
args = parser.parse_args()
root = build.ROOT
if not args.offline:
    subprocess.run([sys.executable, str(root / 'prepare.py')], check=True)
subprocess.run([str(build.JAVA / 'java.exe'), '-cp', ';'.join([str(root / 'deps/libraries/asm.jar'), str(root / 'deps/libraries/asm-commons.jar')]), str(root / 'PortBinary.java'), str(root / 'original.jar'), str(root / 'deps'), str(root / 'ported-base.jar')], check=True)
if build.compile_sources():
    raise SystemExit(1)
package(root / 'projecte-1.0.2-release-fabric-26.3.jar')
