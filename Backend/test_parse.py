import json

# mock response
r = '```json\n{\n  "overallScore": 87,\n  "hookScore": 8,\n  "clarityScore": 7\n}\n```'

# let's write out exactly what App.jsx parseAnalysis does:
clean_str = r.replace('```json', '', 1).replace('```', '', 1).strip()
print(clean_str)

import re

# now with regex:
clean_str2 = re.sub(r'```json', '', r, flags=re.IGNORECASE)
clean_str2 = re.sub(r'```', '', clean_str2).strip()

print(json.loads(clean_str2))
