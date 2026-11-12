"""Small, explicit architectural guard for existing pure rule modules.

This is a source-level guard, not a complete Java dependency graph. It deliberately
does not restrict the existing WorldService transaction/orchestration boundary.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PURE_MODULES = ('WorldMap', 'SceneTopology', 'NpcCombatNavigation', 'NpcPolicy',
                'LogisticsPolicy', 'WeaponRules')
FORBIDDEN = r'\b(?:JdbcTemplate|WorldService|SocketConfig|WebSocketSession|\w+Controller)\b|\borg\.springframework\b|\bjava\.sql\b'


def code_only(source):
    # Preserve code while removing comments and literal contents, including text blocks.
    return re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/', ' ', source)


def check_boundaries(root):
    errors = []
    for name in PURE_MODULES:
        file = root / f'{name}.java'
        if not file.is_file():
            errors.append(f'pure rule module missing: {name}')
            continue
        violations = sorted(set(re.findall(FORBIDDEN, code_only(file.read_text(encoding='utf-8-sig')))))
        errors.extend(f'pure rule dependency: {name} -> {target}' for target in violations)
    return errors


if __name__ == '__main__':
    errors = check_boundaries(ROOT / 'backend/src/main/java/game/world')
    for error in errors:
        print('ERROR:', error)
    print(f'Backend boundaries: {len(PURE_MODULES)} pure modules, {len(errors)} errors')
    raise SystemExit(bool(errors))
