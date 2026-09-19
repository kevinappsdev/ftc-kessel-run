Fields you draw in the editor land here.

**Two seasons ship with this project and both are selectable:** `examples/decode.field.json`
(2025-2026) and `examples/biobuzz.field.json` (2026-2027). Press the **FIELD** chip and pick
one. Nothing about DECODE changed when BIOBUZZ arrived — its golden traces are byte-identical
at every commit of that work, which is the standing evidence.

BIOBUZZ needed four things the field format could not say, and each of them is now a block a
field file can declare rather than a line of Java:

| | |
|---|---|
| `"hives"` | A bi-stable pair of CELLS on an axle that TIPS, swapping which one faces up — the first target here that MOVES, and it carries its AprilTags with it. |
| `"flowers"` | A container that remembers what ORDER things went into it. The bottom-most NECTAR of a colour scores, and the TOP-most owns everything in the tube. |
| `"maxControlled"`, `"preloadPiece"` | The game's own limits on what a robot may hold, beside `maxPreload`. |
| `"opponent"` | The other alliance as a timed script: it drops NECTAR into FLOWERS, which is how one changes hands, and banks MATCH points for WIN and TIE to compare against. The only block in a field file that describes a MATCH rather than a FIELD, and it ships absent. |
| rule types `leave`, `threshold`, `violation`, `beatOpponent` | A rule about the robot's history, a RANKING POINT over other rules, and something the simulator noticed and is reporting for nothing. |

`docs/biobuzz/MEASURED.md` is where every BIOBUZZ number came from, and
`tools/check-step-extract.js` holds those against the competition manual on a machine that has
never seen the field CAD.

A field file may also say how long a match lasts, because period lengths are a property of the
GAME rather than of this simulator:

```json
"match": { "autonomousS": 30, "transitionS": 8, "teleopS": 120, "endgameS": 30 }
```

Leave it out and DECODE's periods are used, which have been the same in every FTC season this
decade. An `@Autonomous` OpMode is stopped at `autonomousS` and everything else at `teleopS`,
the same way the Driver Station stops them — the team's own loop exits and their tidy-up code
runs. `transitionS` is carried and displayed and nothing enforces it, because nothing runs
during a transition.

**Structures and zones** are drawn in the editor now. A **structure** is a thing: a footprint
you click the corners of, a height (or two, for a ramp), a base height for something with air
under it, and `blocksRobot`, which is true unless you argue otherwise — furniture a robot
drives through is a lie a student cannot see through. A **zone** is a rule drawn on the floor:
an area with a name, an alliance and a purpose, that nothing collides with, for the scoring
rules to point at.

The **drop a shape** picker offers a wall, a tower, a ramp, an overhead truss, a basket and
two zones. Every one is a STARTING POINT at a plausible size and says so — the shipped fields'
dimensions come out of a Competition Manual with the clause written beside them, and
duplicating those numbers into a preset would be two places to be wrong about the same figure.
For a season's own shapes, open that season's field and copy the one that is already there.

**Pieces may be shapes.** `"shape"` is `sphere` (the default, so every field that predates
this is unchanged), `box`, `cylinder` or `hexPrism`. A box declares `length`, `width` and
`height`; a cylinder or a hex prism declares a `diameter` across and a `height` along its own
axis. It changes how the piece is drawn, how tall it sits at rest, and how far across it is —
which is what an intake mouth and a claw's reach are compared against. It does **not** change
contact or occlusion, which both work on a bounding sphere and say so.

**`scoring` says what any of it is worth**, and it lives here rather than in the simulator
because what a goal is worth is a fact about a season in exactly the way its height is. A rule
names a zone the pieces have to be at rest in, optionally a piece type, and what one is worth:

```json
"scoring": [
  { "name": "Sample in the high basket", "type": "inZone", "zone": "blueHigh",
    "piece": "sample", "points": 8 },
  { "name": "Parked", "type": "inZone", "subject": "robot", "zone": "blueAscent",
    "points": 3, "when": "endOfMatch" },
  { "name": "Classified in motif order", "type": "motif", "points": 2 }
]
```

`subject` is `piece` unless you say `robot`, which is what a park is. `when` is `always`
unless you say `endOfAuto`, `endOfTeleop` or `endOfMatch` — a park counts at the buzzer and is
latched there, so a robot that drove through the zone in the first ten seconds is not paid for
it. A rule pointing at a zone that does not exist is refused by name, which is why zones are
declared above scoring. **There are no penalties**: every rule scores for the alliance and
nothing subtracts, so a total is an upper bound.

The **MOTIF is not in this file**, deliberately. FIRST leave the OBELISK's placement to the
event, so a field file has nowhere honest to put one — the *choice* of pattern is the team's,
and it is a dropdown on the Driver Station.

`pieceNoun` says what your game calls its pieces, in the singular — `"artifact"`,
`"sample"`, `"cone"`, `"pixel"`. The Driver Station's buttons and counters use it, so a field
with cones on it offers to drop three cones. It defaults to `"piece"`, which is deliberately
generic: a practice field has no game, and it cannot be derived from the piece TYPES either,
because DECODE names its two types `green` and `purple` — a page that guessed from those
would offer to drop three greens onto a field that stages a mixture.

`wallHeightM` says how high the perimeter is. It defaults to 12 in, which is ASSUMED rather
than measured, and it matters now that a distance sensor can be pointed at a wall: a beam that
clears the perimeter reads the room behind it, which on a real field is out of range.

The ones in `examples/` are hand-written and their comments carry where every number came
from — the Competition Manual clause, the figure it was measured off, the tolerance. The
editor opens those but will not save over them, because a drawing tool cannot keep comments
and that provenance is the most valuable thing in those files. Save under a new name and it
arrives in this directory.
