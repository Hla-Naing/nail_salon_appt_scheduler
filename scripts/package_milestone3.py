#!/usr/bin/env python3
"""Package source and submission deliverables; never traverse local secrets/caches."""
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAME = 'CMPE172_Milestone3_FirstName_LastName'
FILES = [
    'README.md', 'pom.xml', 'mvnw', 'mvnw.cmd', '.gitignore', '.gitattributes',
    '.dockerignore', '.env.example', 'Dockerfile', 'docker-compose.yml',
    '.mvn/wrapper/maven-wrapper.properties', '.github/workflows/ci.yml',
    'Milestone3_Demo_Script.md',
]
REQUIRED = [
    'docs/CMPE172_Milestone3_Report.md', 'docs/CMPE172_Milestone3_Report.pdf',
    'docs/Milestone3_Distribution.png', 'docs/Milestone3_Distribution.pdf',
    'docs/Milestone3_Distribution.mmd', 'docs/Milestone3_Verification.md',
    'docs/Milestone3_Test_Results.json',
]
paths = [ROOT / item for item in FILES + REQUIRED]
paths += [p for p in (ROOT / 'src').rglob('*') if p.is_file()]
paths += [p for p in (ROOT / 'scripts').glob('*.py') if p.is_file()]
for p in paths:
    if not p.is_file() or p.is_symlink():
        raise SystemExit(f'Missing file or unexpected symlink: {p.relative_to(ROOT)}')
archive_path = ROOT / f'{NAME}.zip'
with zipfile.ZipFile(archive_path, 'w', zipfile.ZIP_DEFLATED) as archive:
    for p in sorted(set(paths)):
        archive.write(p, Path(NAME) / p.relative_to(ROOT))
with zipfile.ZipFile(archive_path) as archive:
    assert archive.testzip() is None
    print(f'{archive_path.name}: {len(archive.namelist())} files, {archive_path.stat().st_size:,} bytes; integrity OK')
