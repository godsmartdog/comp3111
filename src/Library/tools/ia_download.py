#!/usr/bin/env python3
"""Download a file from Internet Archive using the official python library.

Usage:
  python3 ia_download.py --item <item_id> --file <file_name> --out <output_path>

Credentials:
  Set IA_ACCESS_KEY and IA_SECRET_KEY environment variables.
"""

from __future__ import annotations

import argparse
import os
import sys

import internetarchive as ia


def main() -> int:
    parser = argparse.ArgumentParser(description="Download an IA file via the internetarchive library.")
    parser.add_argument("--item", required=True, help="Internet Archive item identifier (bucket).")
    parser.add_argument("--file", required=True, help="Exact filename within the item.")
    parser.add_argument("--out", required=True, help="Output path for the downloaded file.")
    args = parser.parse_args()

    access_key = os.getenv("IA_ACCESS_KEY", "").strip()
    secret_key = os.getenv("IA_SECRET_KEY", "").strip()
    if not access_key or not secret_key:
        print("Missing IA_ACCESS_KEY or IA_SECRET_KEY in environment.", file=sys.stderr)
        return 1

    session = ia.get_session(config={"s3": {"access": access_key, "secret": secret_key}})
    item = session.get_item(args.item)

    if args.file not in item.files:
        print(f"File not found in item: {args.file}", file=sys.stderr)
        return 1

    item.download(files=[args.file], destdir=os.path.dirname(args.out) or ".")

    downloaded_path = os.path.join(os.path.dirname(args.out) or ".", args.file)
    if os.path.abspath(downloaded_path) != os.path.abspath(args.out):
        os.replace(downloaded_path, args.out)

    print(f"Downloaded to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
