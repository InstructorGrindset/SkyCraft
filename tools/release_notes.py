#!/usr/bin/env python3
"""Release notes for a test build: the commits since the previous release tag, and who made them.

Usage: python3 tools/release_notes.py [--head <commit>] [--previous <tag>]

Prints Markdown: the titles of the commits from the previous release tag (the newest v*-build* tag
before --head, which defaults to HEAD) up to --head, oldest first, merges left out. Titles that name
an area the way this repository's do ("Loot: ...", "Smoke test: ...") are grouped under it; the rest
go under "Other changes", and with fewer than two areas it's one plain list. Then the people to thank:
every commit author and everyone named in a Co-Authored-By line, except bots (the coding assistants
and GitHub's own). With GITHUB_SERVER_URL and GITHUB_REPOSITORY set (GitHub Actions sets them), each
title links its commit. Used by .github/workflows/build.yml for the dev branch's test releases.
"""
import argparse
import os
import re
import subprocess
import sys

# "Area: what changed". An area is one to three words of letters, as the repository's titles use them.
AREA = re.compile(r"^([A-Za-z][A-Za-z'-]*(?: [A-Za-z][A-Za-z'-]*){0,2}): (\S.*)$")
CO_AUTHOR = re.compile(r"^co-authored-by:\s*(.+?)\s*<([^>]*)>\s*$", re.IGNORECASE | re.MULTILINE)
# A GitHub no-reply address carries the account's login: 12345+login@users.noreply.github.com.
GITHUB_NOREPLY = re.compile(r"^(?:\d+\+)?([A-Za-z0-9-]+)@users\.noreply\.github\.com$", re.IGNORECASE)
BOT_NAMES = {"claude", "devin ai", "github-actions", "copilot", "dependabot"}
BOT_EMAILS = {"noreply@anthropic.com", "noreply@github.com"}
OTHER = "Other changes"
MAX_COMMITS = 200


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


def previous_tag(head):
    """The newest release tag before head (on head's own history), or None for the first release."""
    try:
        return git("describe", "--tags", "--abbrev=0", "--match", "v*-build*", head + "^").strip() or None
    except subprocess.CalledProcessError:
        return None


def is_bot(name, email):
    name, email = name.strip().lower(), email.strip().lower()
    return "[bot]" in name or "[bot]" in email or name in BOT_NAMES or email in BOT_EMAILS


def person(name, email):
    """How someone is credited: their name, and their GitHub login when the address gives it away (never the address)."""
    login = GITHUB_NOREPLY.match(email.strip())
    if login and login.group(1).lower() != name.strip().lower():
        return f"{name.strip()} (@{login.group(1)})"
    if login:
        return f"@{login.group(1)}"
    return name.strip()


def commits(head, previous):
    """(sha, author name, author email, message) for each commit after previous up to head, oldest first."""
    span = f"{previous}..{head}" if previous else head
    out = git("log", "--no-merges", "--reverse", f"--max-count={MAX_COMMITS}", "--format=%H%x00%an%x00%ae%x00%B%x1e", span)
    found = []
    for record in out.split("\x1e"):
        record = record.lstrip("\n")
        if not record:
            continue
        sha, name, email, message = record.split("\x00", 3)
        found.append((sha, name, email, message))
    return found


def capitalised(text):
    return text[:1].upper() + text[1:]


def notes(head, previous, link):
    found = commits(head, previous)
    lines = []
    if previous:
        lines.append(f"Changes since {previous}:" if found else f"No new commits since {previous}.")
    else:
        lines.append("Changes:" if found else "No commits.")
    groups = {}  # area -> entries, in the order areas first come up
    for sha, _, _, message in found:
        title = message.strip().splitlines()[0].strip() if message.strip() else "(no title)"
        match = AREA.match(title)
        area, text = (match.group(1), capitalised(match.group(2))) if match else (OTHER, title)
        ref = f"[{sha[:7]}]({link}/commit/{sha})" if link else sha[:7]
        groups.setdefault(area, []).append(f"- {text} ({ref})")
    areas = [a for a in groups if a != OTHER]
    if len(areas) < 2:
        # Too few areas to be worth headings: one list of the whole titles.
        lines.append("")
        for sha, _, _, message in found:
            title = message.strip().splitlines()[0].strip() if message.strip() else "(no title)"
            ref = f"[{sha[:7]}]({link}/commit/{sha})" if link else sha[:7]
            lines.append(f"- {title} ({ref})")
    else:
        for area in areas + ([OTHER] if OTHER in groups else []):
            lines.append("")
            lines.append(f"### {area}")
            lines.extend(groups[area])
    if len(found) >= MAX_COMMITS:
        lines.append("")
        lines.append(f"(The latest {MAX_COMMITS} commits only.)")

    people, seen = [], set()
    for _, name, email, message in found:
        for who, address in [(name, email)] + CO_AUTHOR.findall(message):
            key = address.strip().lower() or who.strip().lower()
            if is_bot(who, address) or key in seen or who.strip().lower() in seen:
                continue
            seen.add(key)
            seen.add(who.strip().lower())
            people.append(person(who, address))
    if people:
        lines.append("")
        lines.append("Thanks to " + (", ".join(people[:-1]) + " and " + people[-1] if len(people) > 1 else people[0]) + " for work in this build.")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--head", default="HEAD", help="the commit being released (default HEAD)")
    parser.add_argument("--previous", help="the previous release tag (default: the newest v*-build* tag before --head)")
    args = parser.parse_args()
    previous = args.previous or previous_tag(args.head)
    server, repo = os.environ.get("GITHUB_SERVER_URL"), os.environ.get("GITHUB_REPOSITORY")
    link = f"{server}/{repo}" if server and repo else None
    sys.stdout.write(notes(args.head, previous, link))


if __name__ == "__main__":
    main()
