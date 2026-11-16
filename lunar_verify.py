"""Verify the JS lunar conversion (LUNAR_INFO + solarToLunar + lunarToSolar)
ported 1:1 from index.html, against all 1212 month JSON files.

Run: python3 lunar_verify.py
Exit 0 = conversion matches the JS original everywhere, with exactly the
two known JS deviations (documented, not fixed -- the native port copies
the JS verbatim, quirks included):
  1) 1059 days where solarToLunar reports isLeap=True for the month right
     after a leap month (e.g. 2001-06-22 -> leap May 2); the picker UI
     self-heals this via its month-list fallback, so it never shows.
  2) 60 days where the LUNAR_INFO table disagrees with the JSON data by
     one day: 2057-09-28..2057-10-27 and 2060-04-30..2060-05-29
     (a whole lunar month shifted). The main UI is JSON-driven and
     unaffected; only the picker's lunar-mode conversion shares the
     web page's own deviation.
"""
import json, glob, re, sys
from datetime import date, timedelta

SRC = "/home/hatch/workspace/shuizu-calendar-android-web-source/index.html"
DATA = "/home/hatch/workspace/shuizu-calendar-android-web-source/calendar-data"

src = open(SRC).read()
m = re.search(r"const LUNAR_INFO = \[(.*?)\];", src, re.S)
LUNAR_INFO = [int(v.strip(), 16) for v in m.group(1).replace("\n", " ").split(",") if v.strip()]
assert len(LUNAR_INFO) == 201, len(LUNAR_INFO)


def leap_month(y):
    return LUNAR_INFO[y - 1900] & 0xF


def leap_days(y):
    return ((LUNAR_INFO[y - 1900] & 0x10000) and 30 or 29) if leap_month(y) else 0


def month_days(y, m):
    return 30 if (LUNAR_INFO[y - 1900] & (0x10000 >> m)) else 29


def year_days(y):
    s = 348
    i = 0x8000
    while i > 0x8:
        if LUNAR_INFO[y - 1900] & i:
            s += 1
        i >>= 1
    return s + leap_days(y)


BASE = date(1900, 1, 31)


def solar_to_lunar(sy, sm, sd):
    offset = (date(sy, sm, sd) - BASE).days
    y = 1900
    while y < 2101 and offset > 0:
        yd = year_days(y)
        if offset < yd:
            break
        offset -= yd
        y += 1
    if y > 2100:
        y = 2100
    leap_m = leap_month(y)
    m = 1
    is_leap = False
    days_of_month = 0
    while m < 13 and offset > 0:
        if leap_m > 0 and m == (leap_m + 1) and not is_leap:
            m -= 1
            is_leap = True
            days_of_month = leap_days(y)
        else:
            days_of_month = month_days(y, m)
        if offset < days_of_month:
            break
        offset -= days_of_month
        if is_leap and m == (leap_m + 1):
            is_leap = False
        m += 1
    if offset == 0 and leap_m > 0 and m == leap_m + 1:
        if is_leap:
            is_leap = False
        else:
            is_leap = True
            m -= 1
    return (y, m, offset + 1, is_leap)


def lunar_to_solar(ly, lm, ld, is_leap):
    offset = 0
    for y in range(1900, ly):
        offset += year_days(y)
    leap_m = leap_month(ly)
    for mm in range(1, lm):
        offset += month_days(ly, mm)
        if leap_m > 0 and mm == leap_m:
            offset += leap_days(ly)
    if is_leap and leap_m == lm:
        offset += month_days(ly, lm)
    offset += ld - 1
    d = BASE + timedelta(days=offset)
    return (d.year, d.month, d.day)


def main():
    files = sorted(glob.glob(DATA + "/*.json"))
    assert len(files) == 1212, len(files)
    n = 0
    md_bad = []
    leap_only = []
    l2s_bad = []
    for p in files:
        dd = json.load(open(p))
        for day in dd["days"]:
            n += 1
            got = solar_to_lunar(day["year"], day["month"], day["day"])
            want = (day["lunarYear"], day["lunarMonth"], day["lunarDay"], day["isLeap"])
            if got[:3] != want[:3]:
                md_bad.append(day["date"])
            elif got[3] != want[3]:
                leap_only.append(day["date"])
            back = lunar_to_solar(day["lunarYear"], day["lunarMonth"], day["lunarDay"], day["isLeap"])
            if back != (day["year"], day["month"], day["day"]):
                l2s_bad.append(day["date"])
    print(f"checked {n} days")
    print(f"month/day mismatches: {len(md_bad)} (expected 60)")
    print(f"isLeap-only mismatches: {len(leap_only)} (expected 1059)")
    print(f"lunar->solar round-trip mismatches: {len(l2s_bad)} (expected 60)")
    ok = True
    exp_md = set()
    d = date(2057, 9, 28)
    while d <= date(2057, 10, 27):
        exp_md.add(d.isoformat()); d += timedelta(days=1)
    d = date(2060, 4, 30)
    while d <= date(2060, 5, 29):
        exp_md.add(d.isoformat()); d += timedelta(days=1)
    if set(md_bad) != exp_md:
        print("MISMATCH: month/day set differs from expected"); ok = False
    if set(l2s_bad) != exp_md:
        print("MISMATCH: round-trip set differs from expected"); ok = False
    for ds in leap_only:
        y, mo, _ = map(int, ds.split("-"))
        ddj = json.load(open(f"{DATA}/{y:04d}-{mo:02d}.json"))
        day = next(x for x in ddj["days"] if x["date"] == ds)
        if day["lunarMonth"] != leap_month(day["lunarYear"]) + 1:
            print("MISMATCH: isLeap-only day not after a leap month:", ds); ok = False; break
    print("OK" if ok else "FAILED")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
