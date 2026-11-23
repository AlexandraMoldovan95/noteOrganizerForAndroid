"""The desktop's memory ceiling, over a tool here that runs its code.

The fixture makers (`make_*_fixture.py`) import the desktop's core into
their own process and run it on cases of their own. On 2026-10-29 a run of
the desktop's tests sized a fixture from a bound a mutant had made a
billion: one process took 43 GB and the computer went down with it. Every
runner there now calls the desktop's `tools/guest.py` first, which holds
the process, and every process it starts, to a ceiling; the tools here
that run the same code call the same guard, from the same file -- never a
copy of it that could drift from the one the desktop keeps.

    from desktop_guest import settle
    settle(DESKTOP)          # before importing anything of the desktop's

Missing, the tool stops and says so, rather than running unguarded.
"""
import importlib.util
import os
import sys

sys.dont_write_bytecode = True      # loading the desktop's guest.py leaves nothing there

# Ceilings for a fixture maker: what one needs is a few tens of MB.
PER_PROCESS_MB = 2048
ALL_TOGETHER_MB = 2048


_settled = None


def settle(desktop, per_process_mb=PER_PROCESS_MB, all_together_mb=ALL_TOGETHER_MB):
    """Hold this process to the desktop's memory ceiling, printing the line
    its guest gives; stop the tool when the guest cannot be found. Once a
    process: the walk asks the export fixture's desktop() more than once."""
    global _settled
    if _settled is not None:
        return _settled
    script = os.path.join(desktop, "tools", "guest.py")
    if not os.path.isfile(script):
        print("cannot find the desktop's tools/guest.py at %s: this tool runs the desktop's own "
              "code and runs it only under the desktop's memory ceiling "
              "(set NOTEORGANIZER_HOME if the desktop lives somewhere else)" % script)
        sys.exit(2)
    spec = importlib.util.spec_from_file_location("desktop_guest_module", script)
    guest = importlib.util.module_from_spec(spec)
    # Held for the run's life: the ceiling's job lives in the module.
    sys.modules[spec.name] = guest
    spec.loader.exec_module(guest)
    for line in guest.settle(per_process_mb, all_together_mb):
        print(line)
    _settled = guest
    return guest
