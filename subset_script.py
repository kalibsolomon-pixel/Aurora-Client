import re
import os

with open(r'src\client\java\com\aurora\client\screen\FeatureIcons.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Extract \uXXXX
codepoints = re.findall(r'\\u([0-9a-fA-F]{4})', content)
# Convert to U+XXXX format
unicodes_str = ','.join([f"U+{cp}" for cp in codepoints])

cmd = f'python -m fontTools.subset full_material.ttf --unicodes="{unicodes_str}" --output-file=new_subset.ttf'
print(cmd)
os.system(cmd)
