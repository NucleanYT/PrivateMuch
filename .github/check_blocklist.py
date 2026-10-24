"""Fails the build if a list shared by the page, the Android app and the Windows app differs anywhere."""
import re
import sys

APP = "android/app/src/main/java/com/nucleanyt/privatemuch/"
WINDOWS = "desktop/lib/filter.js"
SHARED_LISTS = [
    ("BLOCKLIST", "blocked words", [APP + "ContentFilter.java", WINDOWS]),
    ("HOSTLIST", "blocked site names", [APP + "ContentFilter.java", WINDOWS]),
    ("SITELIST", "blocked sites", [APP + "ContentFilter.java", WINDOWS]),
    ("TLDLIST", "web address endings", [APP + "UrlInput.java", WINDOWS]),
    ("COUNTRYLIST", "country endings", [APP + "UrlInput.java", WINDOWS]),
]


def entries(path, marker):
    text = open(path, encoding="utf-8").read()
    block = re.search(marker + r"-START(.*?)" + marker + "-END", text, re.S).group(1)
    block = block.split("\n", 1)[1]  # skip the marker line's own comment
    found = re.findall(r"""['"]([^'"]+)['"]""", block)
    return " ".join(found).split() if marker == "COUNTRYLIST" else found  # country codes are one long string


failed = False
for marker, name, others in SHARED_LISTS:
    page = entries("index.html", marker)
    this_list_ok = True
    for other in others:
        theirs = entries(other, marker)
        if page != theirs:
            failed = True
            this_list_ok = False
            print(f"The {name} differ between index.html and {other}")
            print("  Only in index.html:", sorted(set(page) - set(theirs)))
            print("  Only in " + other + ":", sorted(set(theirs) - set(page)))
    if this_list_ok:
        print(f"The {name} match in all {len(others) + 1} copies ({len(page)} entries).")
sys.exit(1 if failed else 0)
