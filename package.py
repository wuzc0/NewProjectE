import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent
OUTPUTS = ROOT.parent / 'outputs'


def package(destination):
    replacements = {str(path.relative_to(ROOT / 'classes')).replace('\\', '/'): path.read_bytes() for path in (ROOT / 'classes').rglob('*.class')}
    with zipfile.ZipFile(ROOT / 'original.jar') as original:
        metadata = json.loads(original.read('fabric.mod.json'))
        metadata['version'] = '1.0.2-release'
        metadata['name'] = 'ProjectE (Fabric 26.3 Port)'
        metadata['depends'].update(minecraft='~26.3', fabricloader='>=0.19.5', java='>=25', **{'fabric-api': '>=0.163.0'})
        replacements['fabric.mod.json'] = json.dumps(metadata, ensure_ascii=False, indent=2).encode()
        mixins = json.loads(original.read('projecte.mixins.json'))
        mixins['mixins'].extend(['LevelChunkTickerAccessor', 'RebindableTickerAccessor', 'BoundTickerAccessor', 'ItemCraftingRemainderMixin', 'CraftingRecipeRemainderMixin'])
        replacements['projecte.mixins.json'] = json.dumps(mixins, indent=2).encode()
        replacements['data/projecte/damage_type/player_attack.json'] = json.dumps({'message_id': 'player', 'exhaustion': 0.1, 'scaling': 'when_caused_by_living_non_player'}).encode()
        replacements['data/minecraft/tags/damage_type/bypasses_armor.json'] = json.dumps({'replace': False, 'values': ['projecte:player_attack']}).encode()
        conversions = json.loads(original.read('data/projecte/pe_custom_conversions/defaults.json'))
        conversions['values']['before'].append({'type': 'projecte:item', 'id': 'minecraft:ancient_debris', 'emc_value': 8192})
        replacements['data/projecte/pe_custom_conversions/defaults.json'] = json.dumps(conversions, ensure_ascii=False).encode()
        blocks = [Path(name).stem for name in original.namelist() if name.startswith('assets/projecte/blockstates/')]
        for name in blocks:
            item_name = 'interdiction_torch' if name == 'wall_interdiction_torch' else name
            loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'projecte:' + item_name}], 'condition': {'type': 'minecraft:survives_explosion'}}]}
            replacements[f'data/projecte/loot_table/blocks/{name}.json'] = json.dumps(loot).encode()
        pickaxe = ['projecte:' + name for name in blocks if 'torch' not in name and not name.startswith('nova_')]
        replacements['data/minecraft/tags/block/mineable/pickaxe.json'] = json.dumps({'replace': False, 'values': pickaxe}).encode()
        replacements['pack.mcmeta'] = json.dumps({'pack': {'description': 'ProjectE Fabric 26.3 resources', 'min_format': [97, 1], 'max_format': [121, 0]}}).encode()
    destination.parent.mkdir(exist_ok=True, parents=True)
    with zipfile.ZipFile(ROOT / 'ported-base.jar') as original, zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as output:
        for entry in original.infolist():
            if entry.filename in replacements or entry.filename.endswith('PotionBrewingCompat.class'):
                continue
            output.writestr(entry.filename, original.read(entry.filename))
        for name, data in replacements.items():
            output.writestr(name, data)
    print(destination)


if __name__ == '__main__':
    package(ROOT / 'projecte-1.0.2-release-fabric-26.3.jar')
