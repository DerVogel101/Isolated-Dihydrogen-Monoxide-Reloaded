"""Prepare a fresh isolated client run from the frozen benchmark snapshot; never overwrite a run."""
import argparse
import re
import shutil
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('name')
args = parser.parse_args()
if not re.fullmatch(r'[A-Za-z0-9-]+', args.name):
    parser.error('Use letters, digits and hyphens for the run name')
root = Path(__file__).resolve().parents[1]
base = root / 'build/performance'
destination = base / 'runs' / args.name
if destination.exists():
    parser.error(f'Run already exists: {destination}')
if not (base / 'world-snapshot/level.dat').is_file():
    parser.error('Create build/performance/world-snapshot from the closed test save first')
destination.mkdir(parents=True)
shutil.copytree(base / 'world-snapshot', destination / 'saves/benchmark', ignore=shutil.ignore_patterns('session.lock'))
for folder in ['config', 'mods']:
    shutil.copytree(base / 'baseline' / folder, destination / folder)
shutil.copy2(base / 'baseline/options.txt', destination / 'options.txt')
for folder in ['shaderpacks', 'resourcepacks']:
    if (base / 'baseline' / folder).exists():
        shutil.copytree(base / 'baseline' / folder, destination / folder)
print(destination)
