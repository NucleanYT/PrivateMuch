"""Fails the build if a list shared by the page and the Android app differs between the two."""
import re
import sys

APP = "android/app/src/main/java/com/nucleanyt/privatemuch/"
SHARED_LISTS = [
    ("BLOCKLIST", "blocked words", APP + "ContentFilter.java"),
    ("HOSTLIST", "blocked site names", APP + "ContentFilter.java"),
    ("SITELIST", "blocked sites", APP + "ContentFilter.java"),
    ("TLDLIST", "web address endings", APP + "UrlInput.java"),
    ("COUNTRYLIST", "country endings", APP + "UrlInput.java"),
]


def entries(path, marker):
    text = open(path, encoding="utf-8").read()
    block = re.search(marker + r"-START(.*?)" + marker + "-END", text, re.S).group(1)
    block = block.split("\n", 1)[1]  # skip the marker line's own comment
    return re.findall(r"""['"]([^'"]+)['"]""", block)


failed = False
for marker, name, app_file in SHARED_LISTS:
    page, app = entries("index.html", marker), entries(app_file, marker)
    if page != app:
        failed = True
        print(f"The {name} differ between index.html and {app_file}")
        print("  Only in index.html:", sorted(set(page) - set(app)))
        print("  Only in the app:", sorted(set(app) - set(page)))
    else:
        print(f"The {name} match ({len(page)} entries).")
sys.exit(1 if failed else 0)
