#!/usr/bin/env python3
"""Generate initializer.json from initializer.template.json by resolving relative-date tokens.

Tokens:
  {{T@HH:MM[:SS]}}        today UTC midnight at HH:MM[:SS]
  {{T+Nd@HH:MM[:SS]}}     today UTC midnight + N days at HH:MM[:SS]
  {{T-Nd@HH:MM[:SS]}}     today UTC midnight - N days at HH:MM[:SS]
  {{NOW}}                 current instant (second precision)
  {{TODAY}}               today UTC midnight (00:00:00)

All outputs are ISO-8601 UTC, second precision, ending with Z.
"""
import argparse
import re
import sys
from datetime import datetime, time, timedelta, timezone
from pathlib import Path

TOKEN_RE = re.compile(r"\{\{([^{}]+)\}\}")
T_RE = re.compile(r"^T(?:([+-])(\d+)d)?(?:@(\d{2}):(\d{2})(?::(\d{2}))?)?$")


def to_iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def resolve(expr: str, now: datetime, today: datetime) -> str:
    expr = expr.strip()
    if expr == "NOW":
        return to_iso(now)
    if expr == "TODAY":
        return to_iso(today)
    m = T_RE.match(expr)
    if not m:
        raise ValueError(f"unrecognized date token: {{{{{expr}}}}}")
    sign_s, days_s, h_s, mm_s, s_s = m.groups()
    sign = -1 if sign_s == "-" else 1
    days = int(days_s) if days_s else 0
    h = int(h_s) if h_s else 0
    mm = int(mm_s) if mm_s else 0
    s = int(s_s) if s_s else 0
    dt = today + timedelta(days=sign * days, hours=h, minutes=mm, seconds=s)
    return to_iso(dt)


def main() -> int:
    here = Path(__file__).resolve().parent
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--template", default=str(here / "initializer.template.json"))
    ap.add_argument("--out", default=str(here / "initializer.json"))
    args = ap.parse_args()

    now = datetime.now(tz=timezone.utc).replace(microsecond=0)
    today = datetime.combine(now.date(), time(0, 0, 0), tzinfo=timezone.utc)

    template_path = Path(args.template)
    out_path = Path(args.out)

    src = template_path.read_text(encoding="utf-8")
    rendered = TOKEN_RE.sub(lambda m: resolve(m.group(1), now, today), src)
    out_path.write_text(rendered, encoding="utf-8")
    print(f"wrote {out_path} (now={to_iso(now)}, today={to_iso(today)})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
