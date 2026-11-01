# Writing a guide page

One puzzle, one file: `tools/guide/pages/<puzzle id>.txt` (`id` from the puzzle's object; the app will link to `guide/<id>#<technique>`). The index
picks it up. Copy `pages/sudoku.txt` and replace everything. `.txt`, not `.md`: `pages.yml` ignores `**.md`, and the docs-only
merge rule would skip the check.

```bash
python3 tools/guide/build.py --out /tmp/guide    # then serve /tmp/guide and look, at 375x537 and tall, light and dark
./gradlew :app:testDebugUnitTest --tests '*GuideTest*'
```

## Where each fact comes from

| Part of the page | Source |
|---|---|
| `# Title` | the puzzle's `displayName` (Trail, Sun & Moon, Codebreaker, Arrows..., never the code name) |
| `> tagline` | its `tagline` |
| `## How to play` | its `rules` (the gestures), `keyboardHelp`, the "Teaching" notes in `CLAUDE.md`. No hints or undo? Say so (Arrows, Mines) |
| `## Rules` | its `rules`, and the puzzle's section of `CLAUDE.md` for what makes a board fair. Rules only, no strategy |
| `## What changes per tier` | `- Standard: `, `- Hard: `, `- Expert: `, each starting with the exact line from `core/TierNotes.kt`, then anything else the player can see, from `CLAUDE.md` |
| `## Techniques` | `core/TechniqueNames.kt`: every id, in that order, under `### id \| Name`, the name with its first letter capitalised, as Training shows it. Nothing else: mistakes and fallbacks have no name and no entry. A puzzle with no names has no Techniques section |
| What a technique means | the teacher's own `explanation` and the comment at the top of `puzzles/<Name>Teacher.kt` |

Those four sections, in that order, nothing else. Voice: plain, friendly, specific, and short. Never "harder": say what changes. No genre or
trademark names (`GuideTest` holds the list, and `CLAUDE.md`'s section for the puzzle says what it must not be called); add to the list if
your puzzle has one it lacks. Measurements a player cannot see (draws, milliseconds, percentages of a walk) stay out.

Format: blank lines separate paragraphs, `- ` lists, `**bold**`. Nothing else is Markdown.

## Boards

Every technique has at least one. A board is real text, drawn as an inline SVG, so it follows light and dark and needs no capture when the art changes:

```
~~~board
5 3 .
. x _
box: 3
regions: aab acc
focus: r1c1-r1c3
cite: r2c2
answer: r1c3=7
notes: r2c1=27
struck: r1c1=4
lines: r1c1-r1c2 r1c2-r2c2
caption: One or two sentences saying what the board shows. Required.
~~~
```

- **Rows** are space-separated tokens, one per square: any short text, `.` an empty square, `_` no square at all (a clue margin, a hole).
- `box: n` thick walls every n squares; `regions:` one string a row, one letter a square: a tint and walls per letter.
- `focus` the tint (the step's `focus`), `cite` a ring (what the reasoning leans on), `answer` the filled square that is the move.
- `notes` small marks, a digit in its pencil place; `struck` the same, crossed out. Never on the `answer` square.
- `lines` thick lines between square centres (bonds, loops, paths).
- Squares are `r<row>c<col>` from 1, and `r1c1-r3c3` a rectangle.

For what these cannot draw (a hex, a wheel, a tube): first try tokens in a grid (Cipher's sum is rows of letters). If that really cannot
show it, add one small key to `board()` in `build.py`, with its check in `GuideTest`. Do not paste in PNGs, and do not draw for one page what others could share.

**Positions come from the game, never from your head.** Find them:

```bash
DAYBOOK_GUIDE_FIND=<id> ./gradlew :app:testDebugUnitTest --tests '*GuideTest*' --rerun
```

writes `app/build/guide-positions-<id>.txt`: up to 40 positions per technique, each with the seed, the step's `focus`, `cited`, `targets`, its words and the
state as JSON. Pick one where the point is easy to see (few cited squares, short explanation) and copy the board out. Work it through by hand before writing the caption.

**Add your puzzle to `GuideTest.teachers`.** A page with no line there fails. The line turns a board into the puzzle's state and returns what its teacher says
on it; the test then requires that step's technique to be the heading it sits under, its `focus` to be the board's and its `targets` the `answer` squares (a
board whose highlights are not `row * width + col` maps them first). Add what else can be checked cheaply (`sudokuStep`: one answer, the digit, pencil marks really possible).
A teacher that reads more than the rows show (a stored list, a clue) takes the rest from a fixture in the line.

## Before you finish

The page, with its `GuideTest.teachers` line, is the whole change. Look at the rendered page, every board, at phone width in both schemes. Report any fact you
could not trace to a source rather than guessing.
