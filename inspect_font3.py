from fontTools.ttLib import TTFont

f = TTFont(r'c:\Dev\aurora-ui-engine\src\client\resources\assets\aurora\font\material_symbols_rounded.ttf')
cmap = f.getBestCmap()
glyf = f['glyf']

def glyph_stats(cp):
    gname = cmap.get(cp)
    if gname is None:
        return (cp, 'NO_CMAP')
    g = glyf[gname]
    if g.numberOfContours == 0:
        return (cp, gname, 'EMPTY contours=0')
    return (cp, gname, f'contours={g.numberOfContours} '
            f'x[{g.xMin},{g.xMax}] y[{g.yMin},{g.yMax}]')

print('--- 6 PRESENT REPLACEMENT GLYPHS (verify real outlines) ---')
present = [0xE818, 0xEAAC, 0xF014, 0xE800, 0xE1A1, 0xE819]
for cp in present:
    print(glyph_stats(cp))

print('\n--- 2 MISSING REPLACEMENT GLYPHS ---')
for cp in [0xF720, 0xEF06]:
    print(hex(cp), '->', cmap.get(cp))

# Look for plausible present alternates for the 2 missing icons.
# For "deployed_code" (a cube box) alternates: inventory, layers, packages,
# package_2, deployed_code is unique-ish; check a few box-ish icons.
print('\n--- BOX-ISH ALTERNATES (for deployed_code) ---')
for name_hex in ['inventory','inventory_2','package_2','package','layers',
                 'deployed_code','view_in_ar','ar_on_the_way','cube','boxes_rounded']:
    pass  # name->codepoint needs lookup; we already know inventory present

# For "android_cell_4_bar" (signal bars) alternates: signal_cellular_4_bar,
# network_cell, wifi, bar_chart
print('\nsignal_cellular_4_bar U+E1C8 ->', cmap.get(0xE1C8) and glyph_stats(0xE1C8))

# Confirm E1C8 outline
print(glyph_stats(0xE1C8))