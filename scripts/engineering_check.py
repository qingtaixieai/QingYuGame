"""Read-only repository checks; no credentials, network, database or auto-fix."""
import fnmatch
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
MAPPING = re.compile(r'@(Get|Post|Put|Patch|Delete)Mapping\s*\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"')


def scan_routes(root):
    routes, errors = {}, []
    for file in sorted(root.rglob('*.java')):
        source = file.read_text(encoding='utf-8-sig')
        if '@RestController' not in source or '@RestControllerAdvice' in source:
            continue
        prefix = re.search(r'@RequestMapping\s*\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"', source)
        found = list(MAPPING.finditer(source))
        if len(found) != len(re.findall(r'@(Get|Post|Put|Patch|Delete)Mapping\b', source)):
            errors.append(f'unsupported route annotation: {file.name}; extend checker explicitly')
        for match in found:
            key = match[1].upper() + ' ' + (prefix[1] if prefix else '') + match[2]
            if key in routes:
                errors.append(f'duplicate route: {key}')
            routes[key] = file.name
    return routes, errors


def compare_routes(actual, expected):
    return ([f'undocumented route: {r}' for r in sorted(set(actual) - set(expected))]
            + [f'missing route: {r}' for r in sorted(set(expected) - set(actual))])


def owners(path, modules):
    return [name for name, patterns in modules.items() if any(fnmatch.fnmatchcase(path, p) for p in patterns)]


def check_links(file):
    errors = []
    for link in re.findall(r'\]\(([^)]+)\)', file.read_text(encoding='utf-8-sig')):
        link = unquote(link.strip('<>').split('#')[0])
        if not link or re.match(r'[a-zA-Z]+:', link):
            continue
        if not (file.parent / link).exists():
            errors.append(f'broken link: {file.name} -> {link}')
    return errors


def main():
    manifest = json.loads((ROOT / 'config/module-ownership.json').read_text(encoding='utf-8'))
    errors = []
    sources = list((ROOT / 'backend/src/main/java').rglob('*.java'))
    sources += [p for p in (ROOT / 'frontend/src').rglob('*') if p.suffix in {'.ts', '.tsx', '.css'}]
    for file in sources:
        path = file.relative_to(ROOT).as_posix()
        assigned = owners(path, manifest['modules'])
        if len(assigned) != 1:
            errors.append(f'ownership must be unique: {path}: {assigned}')
    routes, route_errors = scan_routes(ROOT / 'backend/src/main/java')
    errors += route_errors
    errors += compare_routes(routes, json.loads((ROOT / 'contracts/http-routes.json').read_text(encoding='utf-8'))['routes'])
    for file in [ROOT / 'docs/交接索引.md', *sorted((ROOT / 'docs/engineering').rglob('*.md'))]:
        errors += check_links(file)
    for error in errors:
        print('ERROR:', error)
    print(f'Engineering checks: {len(sources)} owned sources, {len(routes)} HTTP routes, {len(errors)} errors')
    return bool(errors)


if __name__ == '__main__':
    sys.exit(main())
