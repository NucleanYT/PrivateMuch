"""Fails the build if the blocked-word lists in the page and the Android app differ."""
import re
import sys


def words(path):
    text = open(path, encoding="utf-8").read()
    block = re.search(r"BLOCKLIST-START(.*?)BLOCKLIST-END", text, re.S).group(1)
    block = block.split("\n", 1)[1]  # skip the marker line's own comment
    return re.findall(r"""['"]([^'"]+)['"]""", block)


page = words("index.html")
app = words("android/app/src/main/java/com/nucleanyt/privatemuch/ContentFilter.java")
if page != app:
    print("Blocked-word lists differ between index.html and ContentFilter.java")
    print("Only in index.html:", sorted(set(page) - set(app)))
    print("Only in ContentFilter.java:", sorted(set(app) - set(page)))
    sys.exit(1)
print(f"Blocked-word lists match ({len(page)} entries).")
