path = 'c:/Users/sauba/Desktop/Lytic/Frontend/antigravity-frontend/src/index.css'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

# Fix the trailing bracket issue cleanly
if '/* EOF */' in text:
    text = text.split('/* EOF */')[0].strip() + '\n}'
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print("Fixed trailing brackets in index.css")
else:
    print("Pattern not found, skip")
