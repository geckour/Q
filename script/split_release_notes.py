import re
import sys
from pathlib import Path

LOCALES = ("ja-JP", "en-US")
MAX_LENGTH = 500
HEADING = re.compile(r"^#{1,6}\s*([A-Za-z]{2}-[A-Za-z]{2})\s*$")


def split_sections(body):
    sections = {}
    locale = None
    lines = []

    for line in body.splitlines():
        matched = HEADING.match(line.strip())
        if matched:
            if locale is not None:
                sections[locale] = "\n".join(lines).strip()
            locale = matched.group(1)
            lines = []
        elif locale is not None:
            lines.append(line)

    if locale is not None:
        sections[locale] = "\n".join(lines).strip()

    return sections


def main():
    body_path, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    body = body_path.read_text()
    sections = split_sections(body)

    whatsnew_dir = out_dir / "whatsnew"
    whatsnew_dir.mkdir(parents=True, exist_ok=True)

    too_long = []
    for locale in LOCALES:
        text = sections.get(locale)
        if text is None:
            print(f"::warning::The release notes have no {locale} section.")
            continue
        if len(text) > MAX_LENGTH:
            too_long.append((locale, len(text)))
        (whatsnew_dir / f"whatsnew-{locale}").write_text(text)
        print(f"{locale}: {len(text)} characters")

    (out_dir / "release-notes.txt").write_text(sections.get("ja-JP") or body.strip())

    for locale, length in too_long:
        print(f"::error::The {locale} notes are {length} characters, over the Play limit of {MAX_LENGTH}.")
    if too_long:
        sys.exit(1)


if __name__ == "__main__":
    main()
