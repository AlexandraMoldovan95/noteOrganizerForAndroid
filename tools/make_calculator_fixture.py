"""Rebuild the calculator fixture, using the DESKTOP's own evaluator.

`app/src/test/resources/calculator_cases.json` holds a few hundred sums --
some written to be sums, some random strings of the keys and of things a
paste could bring -- each with what the desktop's
`evaluate_calculator_expression` makes of it and what its
`format_calculator_result` shows. `CalculatorTest` asks the phone's port
the same questions and fails on any answer that differs.

The hand-written suite checks the cases somebody thought of; this checks
the ones nobody did. The sums are drawn from a fixed seed, so the file only
changes when this script or the desktop's evaluator does -- and a change in
the desktop's evaluator is exactly what it exists to catch.

    py tools/make_calculator_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_calculator_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import io
import json
import os
import random
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources",
                       "calculator_cases.json")
SEED = 20261015
BUILT = 300
SCRAMBLED = 300

# The keys, and what a paste can bring that the keys cannot: the ASCII signs
# a keyboard types, digits from other scripts, spaces Python counts and the
# JVM does not, a superscript that looks like a digit and is not one.
KEYS = list("0123456789.") * 3 + list("+−×÷^%√({[)}]") * 2 + [" "]
PASTED = ["-", "*", "/", "٣", "𝟗", "²", "\u00a0", "\u0085", "\u3000", "e", "x", ",", "0.", ".5"]

# Sums somebody thought of: the edges of the power, the rounding, the depth.
CHOSEN = [
    "1÷2048", "123456789.12345678", "0.1+0.2", "0.1×3", "2÷3", "−2÷3", "1÷3×3",
    "2^0.5", "(0−8)^(1÷3)", "(0−2)^3", "0^0", "0^(0−1)", "0×−1", "10^400", "1÷10^400",
    "2^1023", "2^1024", "2^(0−1074)", "0.5^1075", "10^(0−310)", "1.0000001^7097000",
    "1^" + "9" * 400, "(0−1)^" + "9" * 400, "9" * 400 + "^0",
    "(" + "9" * 400 + "−" + "9" * 400 + ")^0", "9" * 400, "9" * 350 + "×" + "9" * 350,
    "1" + "0" * 300, "0." + "0" * 400 + "1", "100000000000×100000000000",
    "5%%", "√−4", "−√4", "1.2.3", "5.", ".5", ".", "5+.", "2(3)", "(3)2", "()", "√",
    "٣+1", "𝟗+1", "2²", "5\u00a0+\u00a03", "5\u0085+3", "5-3", "5*3", "",
    "   ", "(" * 150 + "1" + ")" * 150, "−" * 800 + "1", "√" * 300 + "4",
    "0.00000000005", "0.00000000015", "0.00000000025", "2.675", "1.0000000000499",
    "99999999999.99999999", "−0.00000000004",
]


def number(rng):
    shape = rng.random()
    if shape < 0.45:
        return str(rng.randrange(0, 100))
    if shape < 0.7:
        return "%d.%s" % (rng.randrange(0, 1000), "".join(rng.choice("0123456789") for _ in range(rng.randrange(1, 5))))
    if shape < 0.8:
        return rng.choice(["0", "0.0", "1", "100", "0.5", "2"])
    return str(rng.randrange(0, 10 ** rng.randrange(1, 14)))


def built(rng, depth=0):
    """A sum made to be one, by the grammar the keys spell."""
    parts = [term(rng, depth)]
    for _ in range(rng.randrange(0, 4)):
        parts.append(rng.choice("+−×÷"))
        parts.append(term(rng, depth))
    return "".join(parts)


def term(rng, depth):
    shape = rng.random()
    if depth < 3 and shape < 0.2:
        open_, close = rng.choice([("(", ")"), ("{", "}"), ("[", "]"), ("(", "]")])
        inner = open_ + built(rng, depth + 1) + close
    elif depth < 3 and shape < 0.3:
        inner = "√" + term(rng, depth + 1)
    elif shape < 0.4:
        inner = "−" + term(rng, depth + 1) if depth < 3 else number(rng)
    else:
        inner = number(rng)
    if rng.random() < 0.15:
        inner += "^" + rng.choice(["2", "3", "0.5", "−1", "10", "0", "(1÷3)", "−2"])
    if rng.random() < 0.1:
        inner += "%"
    return inner


def scrambled(rng):
    """Keys in any order, with now and then something only a paste brings."""
    out = []
    for _ in range(rng.randrange(1, 14)):
        out.append(rng.choice(PASTED) if rng.random() < 0.08 else rng.choice(KEYS))
    return "".join(out)


def main():
    if not os.path.isdir(DESKTOP):
        print("cannot find the desktop app at %s" % DESKTOP)
        print("(set NOTEORGANIZER_HOME if it lives somewhere else)")
        return 2
    sys.path.insert(0, HERE)
    from desktop_guest import settle
    settle(DESKTOP)      # the desktop's memory ceiling, before its code runs here
    sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.calculator import (
            evaluate_calculator_expression, format_calculator_result)
    except ImportError as problem:
        print("cannot import the desktop evaluator: %s" % problem)
        return 2

    rng = random.Random(SEED)
    exprs = list(CHOSEN)
    exprs += [built(rng) for _ in range(BUILT)]
    exprs += [scrambled(rng) for _ in range(SCRAMBLED)]

    sums = []
    for expr in exprs:
        value = evaluate_calculator_expression(expr)
        sums.append({
            "expr": expr,
            # repr, as text: the shortest form that reads back as the same double.
            "value": None if value is None else repr(value),
            "shown": None if value is None else format_calculator_result(value),
        })

    # The display on its own, over numbers no sum above happened to give.
    values = [rng.uniform(-1e6, 1e6) for _ in range(150)]
    values += [rng.uniform(-1, 1) * 10 ** rng.randrange(-14, 18) for _ in range(150)]
    values += [k / 2 ** 11 for k in range(1, 40)] + [0.5e-10 * k for k in range(1, 40)]
    shown = [{"value": repr(v), "shown": format_calculator_result(v)} for v in values]

    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"seed": SEED, "sums": sums, "shown": shown}, f, ensure_ascii=False, indent=0)
    answered = sum(1 for s in sums if s["value"] is not None)
    print("%d sums (%d with an answer), %d shown values" % (len(sums), answered, len(shown)))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
