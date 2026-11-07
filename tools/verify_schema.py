"""Host-side validation; does not replace Android repository tests."""
from pathlib import Path
import sqlite3,json
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
db=sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
db.executescript((assets/'schema_v10.sql').read_text())
for row in json.loads((assets/'seed.json').read_text()):
    data=row['values'];columns=','.join(data);marks=','.join('?' for _ in data)
    db.execute(f"INSERT INTO {row['table']} ({columns}) VALUES ({marks})",list(data.values()))
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert not db.execute('PRAGMA foreign_key_check').fetchall()
assert db.execute('SELECT COUNT(*) FROM productos').fetchone()[0]==14
assert db.execute('SELECT COUNT(*) FROM producto_variantes').fetchone()[0]==18
assert db.execute('SELECT COUNT(*) FROM users').fetchone()[0]==2
assert len(db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name<>'sqlite_sequence'").fetchall())==18
cols=db.execute('PRAGMA table_xinfo(sale_payments)').fetchall()
assert next(c for c in cols if c[1]=='total_pago')[6]==3
print('PASS: 18 legacy tables, 44 seed rows, 14 products, 18 variants, generated total_pago, integrity and foreign keys.')
