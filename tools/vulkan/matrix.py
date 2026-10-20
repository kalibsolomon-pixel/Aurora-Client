"""Run prepared, immutable renderer fixtures sequentially, then inspect pixels."""
import sys
from reproduce import ROOT, launch
from review import review

for name in sys.argv[1:]:
    root = ROOT / name
    if launch(root):
        raise SystemExit('Abnormal client exit for ' + name)
    if review(root):
        raise SystemExit('Evidence failed for ' + name)
