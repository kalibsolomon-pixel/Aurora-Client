import re
import subprocess

with open(r'src\client\java\com\aurora\client\screen\FeatureIcons.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Extract \uXXXX
codepoints = re.findall(r'\\u([0-9a-fA-F]{4})', content)
# Convert to U+XXXX format
unicodes_str = ','.join([f"U+{cp}" for cp in codepoints])

# unicodes_str derives from a repo source file; it is passed as one list
# argument (never interpolated into a shell command string).
print(f'Subsetting {len(codepoints)} codepoints: {unicodes_str}')
subprocess.run(
    ['python', '-m', 'fontTools.subset', 'full_material.ttf',
     f'--unicodes={unicodes_str}', '--output-file=new_subset.ttf'],
    shell=False)
