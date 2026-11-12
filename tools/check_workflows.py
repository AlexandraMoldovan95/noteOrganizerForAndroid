"""Fail when a workflow gives CI more rights than it needs, or runs code it did not name.

WHY THIS GATE EXISTS
--------------------
GitHub gives each workflow run a token for the repository. A workflow that
does not say what the token may do gets whatever the repository's setting
allows, which can include pushing, editing issues and publishing releases.
Nothing here needs any of that, so every workflow says so
(`permissions: contents: read`); the desktop's code is read with a token
of its own, DESKTOP_REPO_TOKEN, which this does not see. An action named
by a tag (`@v4`) runs whatever its owner moves that tag to next, so every
action is pinned to the commit its tag named when it was chosen, the tag
written beside it. Both were added on 2026-11-12, before the repository
went public, with the desktop's twin of this gate; this holds them for the
next workflow, which nobody will remember them for.

WHAT IT CHECKS
--------------
In every file under .github/workflows:

1. a `permissions:` key at the top of the file;
2. every `permissions:` there and in its jobs grants `read` or `none`,
   never `write` (an inline `{}` or `read-all` is read-only too);
3. every `uses:` of an action or a workflow from another repository is
   pinned to a full commit -- `owner/repo@<40 hex> # <tag>` -- and a
   `docker://` image to its `@sha256:` digest;
4. no `pull_request_target` or `workflow_run` trigger: both run with the
   repository's secrets and a token that can write, on events anyone
   with a fork can cause.

To move an action to a newer release, read the commit its tag names now
and write both: `git ls-remote --tags https://github.com/<owner>/<repo>`,
taking the `^{}` line where there is one (that is the commit, not the
tag).

    py tools/check_workflows.py
"""
import glob
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
READ_ONLY = {"read", "none"}
SHA = re.compile(r"^[^@\s]+@[0-9a-f]{40}$")
USES = re.compile(r"""^\s*(?:-\s+)?uses:\s*["']?([^"'\s#]+)["']?\s*(?:#\s*(\S.*))?$""")
KEY = re.compile(r"^(\s*)(?:-\s+)?([A-Za-z_][\w-]*):\s*(.*)$")
TRIGGERS = re.compile(r"\b(pull_request_target|workflow_run)\b")


def without_comment(line):
    """The line up to its comment: a '#' at the start or after a space."""
    match = re.search(r"(^|\s)#", line)
    return line[:match.start()].rstrip() if match else line.rstrip()


def problems_in(path):
    with io.open(path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()
    name = os.path.relpath(path, ROOT).replace(os.sep, "/")
    found, pinned, top_level = [], 0, False
    i = 0
    while i < len(lines):
        raw, line = lines[i], without_comment(lines[i])
        where = "%s:%d" % (name, i + 1)
        if TRIGGERS.search(line):
            found.append("%s: the %s trigger runs with the repository's secrets "
                         "and a token that can write" % (where, TRIGGERS.search(line).group(1)))
        uses = USES.match(raw)
        if uses:
            ref, tag = uses.group(1), uses.group(2)
            if ref.startswith("./"):
                pass
            elif ref.startswith("docker://"):
                if "@sha256:" not in ref:
                    found.append("%s: %s is not pinned to its @sha256: digest" % (where, ref))
                else:
                    pinned += 1
            elif not SHA.match(ref) or not tag:
                found.append("%s: %s is not pinned to a commit with its tag beside it "
                             "(owner/repo@<40 hex> # <tag>)" % (where, ref))
            else:
                pinned += 1
        key = KEY.match(line)
        if key and key.group(2) == "permissions":
            indent, value = len(key.group(1)), key.group(3).strip()
            if indent == 0:
                top_level = True
            if value:
                if value not in ("{}", "read-all"):
                    found.append("%s: permissions: %s may write; write it as a block "
                                 "of `scope: read`" % (where, value))
            else:
                j = i + 1
                while j < len(lines):
                    entry = without_comment(lines[j])
                    if not entry.strip():
                        j += 1
                        continue
                    sub = KEY.match(entry)
                    if not sub or len(sub.group(1)) <= indent:
                        break
                    if sub.group(3).strip() not in READ_ONLY:
                        found.append("%s:%d: permissions grant %s: %s; only read or none"
                                     % (name, j + 1, sub.group(2), sub.group(3).strip()))
                    j += 1
        i += 1
    if not top_level:
        found.append("%s: no `permissions:` at the top of the file, so its token "
                     "gets whatever the repository's setting allows" % name)
    return found, pinned


def main():
    files = sorted(glob.glob(os.path.join(ROOT, ".github", "workflows", "*.yml"))
                   + glob.glob(os.path.join(ROOT, ".github", "workflows", "*.yaml")))
    if not files:
        print("FAIL  no workflow found under .github/workflows: nothing was checked.")
        return 1
    problems, pinned = [], 0
    for path in files:
        found, count = problems_in(path)
        problems += found
        pinned += count
    for problem in problems:
        print("  FAIL  " + problem)
    if problems:
        print("%d problem(s) in the workflows: what each rule is for is in "
              "tools/check_workflows.py." % len(problems))
        return 1
    print("Workflows hold: %d file(s), each token read-only, %d action(s) pinned to a "
          "commit, no trigger that hands a fork the repository's rights." % (len(files), pinned))
    return 0


if __name__ == "__main__":
    sys.exit(main())
