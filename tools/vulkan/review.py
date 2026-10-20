"""Check completed native evidence, including an orientation/blur regression."""
import argparse
import json
from pathlib import Path
import statistics
from PIL import Image


def review(root):
    report = json.loads((root / 'report.json').read_text())
    checks = {entry['check']: entry['passed'] for entry in report['checks']}
    checks['normal-process-exit'] = json.loads((root / 'exit.json').read_text())['exitCode'] == 0
    shots = root / 'game/screenshots'
    images = {path.stem: Image.open(path).convert('RGB') for path in shots.glob('*.png')}
    checks['captures-contain-rendered-content'] = all(max(v[1] for v in image.getextrema()) > 30 for image in images.values())
    # The panel has fixed GUI dimensions at scale 2 and stays centered. Windows
    # DPI/window restoration can change the framebuffer from the requested size;
    # translate the original samples with its center, without scaling the panel.
    def pixel(image, x, y):
        return image.getpixel((round(x + (image.width - 1280) / 2),
                               round(y + (image.height - 720) / 2)))
    solid = images['11-glass-solid']
    checks['solid-negative-control'] = max(abs(a-b) for a,b in zip(pixel(solid,700,380),pixel(solid,950,380))) <= 1
    stripes = images['12-glass-stripes']
    outside = [statistics.mean(pixel(stripes,950,y)) for y in range(305,440)]
    inside = [statistics.mean(pixel(stripes,700,y)) for y in range(305,440)]
    correlation = statistics.correlation(outside,inside)
    attenuation = statistics.pstdev(inside)/statistics.pstdev(outside)
    checks['stripe-orientation-and-phase'] = correlation > .85
    checks['gaussian-reduces-stripe-contrast'] = .1 < attenuation < .98
    raised = images['13-glass-raised']
    depressed = images['14-glass-depressed']
    def light_difference(rows):
        return statistics.mean(statistics.mean(pixel(raised,700,y)) - statistics.mean(pixel(depressed,700,y)) for y in rows)
    upper = light_difference(range(298,307))
    lower = light_difference(range(420,440))
    checks['raised-depressed-light-direction'] = upper > 0 and lower < 0
    log = (root / 'stdout.log').read_text(encoding='utf-8',errors='replace')
    for name, message in [('no-glass-failure','blur panels disabled'),('no-wrong-thread-release','Rendersystem called from wrong thread'),('no-texture-close-failure','Failed to close texture'),('no-native-crash','A fatal error has been detected'),('no-shader-compile-failure','Failed to compile shader')]:
        checks[name] = message not in log
    checks['no-device-pipeline-compile-failure'] = not any(message in log for message in ("Couldn't compile",'Caught exception compiling pipeline','Exception while compiling pipeline'))
    output = dict(backend=report['backend'],frames=report['frames'],worldFrames=report['worldFrames'],
                  captures=len(images),stripeCorrelation=correlation,contrastRatio=attenuation,
                  upperLightingDelta=upper,lowerLightingDelta=lower,checks=checks)
    (root / 'review.json').write_text(json.dumps(output,indent=2))
    failures = [key for key,value in checks.items() if not value]
    print(json.dumps(dict(backend=report['backend'],checks=len(checks),captures=len(images),failed=failures,
                         stripeCorrelation=correlation,contrastRatio=attenuation),indent=2))
    return int(bool(failures))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root',type=Path)
    raise SystemExit(review(parser.parse_args().root))
