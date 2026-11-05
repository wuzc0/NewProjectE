import concurrent.futures
import hashlib
import json
import sys
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent


def fetch(url, path):
    path = Path(path)
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(url, timeout=120) as response:
            path.write_bytes(response.read())
    return path


def read_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


if __name__ == '__main__':
    original = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else ROOT / 'original.jar'
    if not original.exists():
        raise SystemExit('Place the supplied projecte-1.0.jar at original.jar, or pass its path as an argument.')
    if hashlib.sha256(original.read_bytes()).hexdigest() != '855e48329f788fd7039d506620c54eec88ad8e4c3d71f209ede41b1aac4b8257':
        raise SystemExit('Unexpected original JAR: this patch targets the supplied 26.1.2 binary only.')
    fetch(original.as_uri(), ROOT / 'original.jar')
    manifest = read_json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json')
    version = next(entry for entry in manifest['versions'] if entry['id'] == '26.3')
    metadata = read_json(version['url'])
    (ROOT / 'minecraft-26.3.json').write_text(json.dumps(metadata, indent=2))
    jobs = [(metadata['downloads']['client']['url'], ROOT / 'deps/minecraft-client.jar'),
            (metadata['downloads']['server']['url'], ROOT / 'deps/minecraft-server-bundle.jar'),
            ('https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar', ROOT / 'deps/fabric-loader.jar'),
            ('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.163.0+26.3/fabric-api-0.163.0+26.3.jar', ROOT / 'deps/fabric-api.jar'),
            ('https://maven.fabricmc.net/net/fabricmc/sponge-mixin/0.17.0+mixin.0.8.7/sponge-mixin-0.17.0+mixin.0.8.7.jar', ROOT / 'deps/mixin.jar')]
    for library in metadata['libraries']:
        artifact = library.get('downloads', {}).get('artifact')
        if artifact and '-natives-' not in artifact['path']:
            jobs.append((artifact['url'], ROOT / 'deps/libraries' / Path(artifact['path']).name))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = {pool.submit(fetch, url, path): path for url, path in jobs}
        for future in concurrent.futures.as_completed(futures):
            try:
                print(f'Downloaded {future.result().name}', flush=True)
            except Exception as error:
                print(f'FAILED {futures[future]}: {error}', flush=True)
    api = ROOT / 'deps/fabric-api.jar'
    if api.exists():
        with zipfile.ZipFile(api) as archive:
            for entry in archive.namelist():
                if entry.endswith('.jar'):
                    (ROOT / 'deps/libraries' / Path(entry).name).write_bytes(archive.read(entry))
    with zipfile.ZipFile(ROOT / 'deps/minecraft-server-bundle.jar') as archive:
        for name in archive.namelist():
            if name.startswith('META-INF/versions/') and name.endswith('.jar'):
                (ROOT / 'deps/minecraft-server.jar').write_bytes(archive.read(name))
    profile = read_json('https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/server/json')
    (ROOT / 'fabric-server.json').write_text(json.dumps(profile, indent=2))
    for library in profile['libraries']:
        group, artifact, version = library['name'].split(':')
        name = f'{artifact}-{version}.jar'
        fetch(f"{library['url']}{group.replace('.', '/')}/{artifact}/{version}/{name}", ROOT / 'deps/libraries' / name)
    fetch('https://repo.maven.apache.org/maven2/org/ow2/asm/asm/9.9.1/asm-9.9.1.jar', ROOT / 'deps/libraries/asm.jar')
    fetch('https://repo.maven.apache.org/maven2/org/ow2/asm/asm-commons/9.9.1/asm-commons-9.9.1.jar', ROOT / 'deps/libraries/asm-commons.jar')
    fetch('https://repo.maven.apache.org/maven2/org/jetbrains/annotations/26.0.2/annotations-26.0.2.jar', ROOT / 'deps/libraries/annotations.jar')
