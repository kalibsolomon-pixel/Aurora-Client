"""Summarize preserved native mapping fixtures; never launches or mutates a game.

Requires Pillow only for contact sheets/crops. Raw screenshots remain the evidence;
contact sheets are explicitly downsampled layout overviews, crops are native 1:1.
"""
import argparse
import bisect
import csv
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile
from PIL import Image, ImageDraw


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def percentile(values, fraction):
    values = sorted(values)
    p = (len(values) - 1) * fraction
    low = int(p)
    return values[low] + (values[min(low + 1, len(values) - 1)] - values[low]) * (p - low)


def rotation(root, report):
    with (root / 'acceptance-frames.csv').open() as stream:
        frames = [dict(head=int(r['head_ns']), state=int(r['state']))
                  for r in csv.DictReader(stream) if r['stage'] == '310']
    start = frames[0]['head']
    frames = [r for r in frames if 2e9 <= r['head'] - start < 14e9]
    times = [(b['head'] - a['head']) / 1e6 for a, b in zip(frames, frames[1:])]
    clock = report['clock']
    offset = clock['qpcTick'] * 1e9 / clock['qpcFrequency'] - (clock['nanoBefore'] + clock['nanoAfter']) / 2
    first, last = frames[0]['head'] + offset, frames[-1]['head'] + offset
    with (root / 'acceptance-process.csv').open() as stream:
        samples = [{k: int(v) for k, v in r.items()} for r in csv.DictReader(stream)]
    sample_times = [r['monotonic_ns'] for r in samples]

    def cpu(at):
        i = bisect.bisect_right(sample_times, at)
        assert 0 < i < len(samples), 'CPU samples must bracket the measured interval'
        a, b = samples[i - 1], samples[i]
        weight = (at - a['monotonic_ns']) / (b['monotonic_ns'] - a['monotonic_ns'])
        return a['cpu_time_ns'] + weight * (b['cpu_time_ns'] - a['cpu_time_ns'])

    return dict(frames=len(frames), seconds=(last-first)/1e9,
                fps=(len(frames)-1)*1e9/(last-first), medianMs=percentile(times,.5),
                p95Ms=percentile(times,.95), p99Ms=percentile(times,.99),
                over50ms=sum(t>50 for t in times), invalidFocusFrames=sum(not r['state']&2 for r in frames),
                cpuPercentOneCore=(cpu(last)-cpu(first))/(last-first)*100,
                peakRssMiB=max(r['rss_bytes'] for r in samples if first<=r['monotonic_ns']<=last)/2**20)


def summary(root):
    report = json.loads((root/'acceptance-report.json').read_text())
    process = json.loads((root/'acceptance-process.json').read_text())
    assert process['exitCode']==0 and all(c['passed'] for c in report['checks'])
    log=(root/'acceptance-stdout.log').read_text(encoding='utf-8',errors='replace')
    return dict(report=report, process=process,
                logChecks=dict(wrongRenderThread=log.count('Rendersystem called from wrong thread'),
                               failedTextureClose=log.count('Failed to close texture')),
                runtimeManifestSha256=sha(root/'runtime-manifest.json'),
                probeSha256=sha(root/'tooling/aurora-mapping-acceptance.jar'))


def generate(reference, candidate, functional, output):
    output.mkdir(parents=True, exist_ok=True)
    render_path=output/'render-results.json'
    render=json.loads(render_path.read_text()) if render_path.exists() else {}
    roots=[reference,candidate]
    # Retained optimization attempts use the same interval/clock interpolation.
    roots += [candidate.parent/f'aurora-mapping-pixel-candidate-{i}' for i in ('06','07','08')
              if (candidate.parent/f'aurora-mapping-pixel-candidate-{i}/acceptance-report.json').exists()]
    for root in roots:
        entry=summary(root)
        entry['renderEvidence']=json.loads((root/'render-evidence.json').read_text())
        entry['rotation4K']=rotation(root,entry['report'])
        entry['rotationMethod']='Stage 310 heads, 2 <= elapsed < 14 seconds; linear CPU endpoint interpolation after native QPC calibration; peak sampled RSS in interval.'
        render[root.name.removeprefix('aurora-mapping-pixel-')]=entry
    render_path.write_text(json.dumps(render,indent=2)+'\n')
    checks=summary(functional)
    for root in (candidate,functional):
        logs=json.loads((root/'acceptance-log-checks.json').read_text())
        assert not any(logs.values()), 'Native GPU cleanup log checks failed'
    checks['sourceFixture']=functional.name
    checks['logChecks']=json.loads((functional/'acceptance-log-checks.json').read_text())
    checks['method']='Separate disposable fixture; full native workflow with transactional color-preview checks.'
    (output/'functional-results.json').write_text(json.dumps(checks,indent=2)+'\n')
    screenshots=candidate/'game/screenshots'
    sources=[]
    cases=json.loads((candidate/'render-evidence.json').read_text())
    for surface in ('hud','map','manager','editor'):
        sheet=Image.new('RGB',(1920,760),(20,22,28))
        draw=ImageDraw.Draw(sheet)
        for i,case in enumerate(cases):
            path=screenshots/f'mapping-pixels-{i}-{surface}.png'
            with Image.open(path) as raw:
                assert raw.size==(case['width'],case['height'])
                sources.append(dict(sourceFixture=candidate.name,file=path.name,width=raw.width,height=raw.height,sha256=sha(path)))
                x,y=(i%3)*640,(i//3)*380
                draw.text((x+8,y+3),f"{raw.width}x{raw.height} / GUI {case['guiScale']} / HUD {case['hudScale']}",fill='white')
                sheet.paste(raw.convert('RGB').resize((640,360),Image.Resampling.LANCZOS),(x,y+20))
        sheet.save(output/f'final-{surface}-scales.png')
    for surface,i in (('manager',2),('editor',5),('map',4)):
        shutil.copy2(screenshots/f'mapping-pixels-{i}-{surface}.png',output/f'final-{surface}-native.png')
    crops=[]
    definitions=[('before-circle-edge-4k.png',reference,4,'hud',(3340,40,3460,180)),
                 ('after-circle-edge-4k.png',candidate,4,'hud',(3340,40,3460,180)),
                 ('before-minimap-4k.png',reference,4,'hud',(3270,0,3840,680)),
                 ('after-minimap-4k.png',candidate,4,'hud',(3270,0,3840,680)),
                 ('final-editor-detail-4k.png',candidate,5,'editor',(888,600,1488,850)),
                 ('final-square-edge-4k.png',candidate,5,'hud',(2835,18,2935,118)),
                 ('final-map-markers-4k.png',candidate,4,'map',(1710,1030,2210,1430)),
                 ('final-world-marker-4k.png',candidate,4,'hud',(3200,1300,3800,1850))]
    for name,root,i,surface,rect in definitions:
        source=root/f'game/screenshots/mapping-pixels-{i}-{surface}.png'
        with Image.open(source) as raw:
            raw.crop(rect).save(output/name)
        crops.append(dict(file=name,sourceFixture=root.name,source=source.name,sourceSha256=sha(source),crop=rect,scale=1))
    (output/'screenshot-sources.json').write_text(json.dumps(sources,indent=2)+'\n')
    (output/'native-crops.json').write_text(json.dumps(crops,indent=2)+'\n')
    repo=Path(__file__).resolve().parents[1]
    tests=[ET.parse(p).getroot() for p in (repo/'build/test-results/test').glob('TEST-*.xml')]
    totals={k:sum(int(t.get(k,0)) for t in tests) for k in ('tests','failures','errors','skipped')}
    assert totals['tests'] and not any(totals[k] for k in ('failures','errors','skipped'))
    jar=repo/'build/libs/aurora-3.0.0.jar'
    with zipfile.ZipFile(jar) as production:
        assert not any(p.startswith(('mappingprobe/','mappingperf/','research/')) for p in production.namelist())
    manifests=[json.loads((r/'runtime-manifest.json').read_text()) for r in (candidate,functional)]
    assert manifests[0]==manifests[1]
    for entry in manifests[0]:
        assert sha(repo/entry['path'])==entry['sha256'], 'Native snapshot differs from current build'
    validation=dict(java=21,command='gradlew.bat test build',tests=totals,
                    productionJar=jar.name,productionJarSha256=sha(jar),diagnosticClassesInProduction=False,
                    finalRuntimeManifestSha256=sha(candidate/'runtime-manifest.json'),
                    nativeRenderingChecks=len(summary(candidate)['report']['checks']),
                    nativeFunctionalChecks=len(checks['report']['checks']),
                    nativeGpuCleanupLogChecksPassed=True,
                    finalNativeSnapshotsMatchCurrentBuild=True)
    (output/'validation-results.json').write_text(json.dumps(validation,indent=2)+'\n')
    print(json.dumps({k:v['rotation4K'] for k,v in render.items()},indent=2))


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('reference','candidate','functional','output'):
        parser.add_argument(name,type=Path)
    args=parser.parse_args()
    generate(args.reference,args.candidate,args.functional,args.output)
