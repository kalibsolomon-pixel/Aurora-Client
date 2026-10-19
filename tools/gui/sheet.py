"""Contact sheets / crops for reviewing native captures (never resamples the source files)."""
import sys
from pathlib import Path
from PIL import Image

def sheet(out, paths, cols=2, width=960):
    imgs = [Image.open(p).convert('RGB') for p in paths]
    thumbs = [i.resize((width, round(i.height * width / i.width)), Image.LANCZOS) for i in imgs]
    h = max(t.height for t in thumbs)
    rows = (len(thumbs) + cols - 1) // cols
    canvas = Image.new('RGB', (cols * width, rows * h), (255, 0, 255))
    for n, t in enumerate(thumbs):
        canvas.paste(t, ((n % cols) * width, (n // cols) * h))
    canvas.save(out)

def crop(out, path, box):
    Image.open(path).crop(tuple(int(v) for v in box.split(','))).save(out)

if __name__ == '__main__':
    if sys.argv[1] == 'crop':
        crop(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        cols = int(sys.argv[2]); out = sys.argv[3]
        sheet(out, sys.argv[4:], cols)
