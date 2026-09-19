# Your own parts

Drop a `*.part.json` file in here and it appears in the garage's toolbox, for you and for anyone
you send this folder to.

This exists because of a limit that is not going away on its own: the shipped catalogue only
contains parts whose dimensions can be *sourced*. Nobody here is going to type goBILDA's CAD in
from memory — a number invented to look plausible is the exact failure this whole program spends
its time refusing, and at catalogue scale it would be a hundred of them.

A team with a caliper does not have that problem. Measure your part once, write it down here with
where the numbers came from, and it is measured for everybody.

## The format

```json
{
  "id": "myteam-shooter-hood",
  "group": "Mechanisms",
  "name": "Our hood",
  "blurb": "The printed hood off the 2026 shooter.",
  "confidence": "measured",
  "source": "Calipers, 3 Sept 2026. 120 mm across the face, 40 mm deep, 34 mm tall.",
  "envelope": { "shape": "box", "lengthIn": 1.57, "widthIn": 4.72, "heightIn": 1.34 },
  "mechanism": { "type": "hood", "angleAtZero": 55, "angleAtOne": 20 }
}
```

Every field is required except `envelope`.

- **`confidence`** must be `measured`, `nominal` or `yours`. It becomes the badge on the part, and
  it is the only thing telling the next person whether anybody actually measured this.
- **`source`** must say where the numbers came from. A part that cannot say is worse than no part,
  because it looks exactly like one that can.
- **`envelope`** is how big it is — `{"shape":"box","lengthIn":..,"widthIn":..,"heightIn":..}`
  or `{"shape":"cylinder","diameterIn":..,"lengthIn":..}`. It is what the garage draws, and since
  the robot got a shape it is also written into your `robot.json` when you place the part, where
  the simulator reads it as a solid box: a hood in front of a camera hides what is behind it. A
  cylinder is written as its own diameter square, because what reads it is a bounding box either
  way. **A part with no `envelope` blocks nothing** — an unmeasured part has no size anybody can
  vouch for, and inventing one would put a guess where a team would read a measurement.
- **`structure`** is the third thing a part can be: something really there that the robot does
  not talk to — a bracket, a length of channel, the battery, a hub. It declares no hardware and
  writes no port, and it still has a size, a place and a weight, so it counts toward whether the
  robot fits and toward how hard it is to turn. `{"what":"battery","massG":567}`.
- **`device`** or **`mechanism`** is what dropping it WRITES into your robot.json. This *is* read,
  by the drivetrain, the vision model and the odometry.

A part in the **Chassis** group writes a whole drivetrain: the motors, their roles, their
mounting and `chassis.type`. There are four of them — mecanum, two-wheel tank, six-wheel
drop-centre tank, and X-drive — and a robot can only have one, so adding a second is not a
thing the garage offers. `role` for a mecanum or an X-drive names a CORNER
(`drive.frontLeft` and friends, all four required); for a tank it names a SIDE
(`drive.left` / `drive.right`, or `drive.leftFront` and friends for four and six wheels, with
the same count on each side). `drive.frontLeft` and `drive.leftFront` are the same wheel and
either spelling is accepted.

A device's `type` has to be one the simulator actually builds: `DcMotorEx`, `Servo`, `CRServo`,
`Limelight3A`, `GoBildaPinpointDriver`, `IMU`, `BNO055IMU`, `Rev2mDistanceSensor`,
`RevColorSensorV3`, `TouchSensor` or `AnalogInput`. A mechanism's has to be `flywheel`, `hood`,
`intake`, `feeder`, `linearSlide`, `arm`, `turret` or `claw`. Anything else would draw, save,
and then simulate nothing.

Some types read extra keys, and the extras are the interesting part:

- **`IMU`** takes `logoDirection` and `usbDirection` — how the hub is ACTUALLY bolted on, in
  REV's own vocabulary. What your code passes to `IMU.initialize()` is what it BELIEVES, and
  the simulator models both, because the two disagreeing is the most common IMU fault on a
  real robot.
- **`Rev2mDistanceSensor`**, **`RevColorSensorV3`** and **`TouchSensor`** are all about the
  `mount`. It is not decoration: the mount's position and its yaw and pitch ARE the beam. A
  rangefinder at 6 in ranges to a game piece; the same one at 8 in looks over the top of it.
- **`TouchSensor`** also takes `triggerDistanceM`, how close something has to be for the
  switch to close. It defaults to 15 mm and that default is ASSUMED.
- **`AnalogInput`** takes `mechanism`, the mechanism the potentiometer is geared to.
- **`DcMotorEx`** takes `encoderOnly: true` with `pod: "forward"` or `"strafe"` — a motor port
  with an encoder plugged in and no motor on it, which is how nearly every three-wheel
  dead-wheel localizer is wired. Such a port needs no `motor` spec, because there is no motor.
- **`feeder`** can be one stretch of an **ordered magazine**: `pathFromM`/`pathToM` or a
  per-size `reach` list, `wheelDiameterM`, and `firesAtM` on the one that meets the flywheel. A
  robot whose feeders say where they reach has balls with positions instead of a count, and a
  ball only moves while a wheel that touches it is running. `OrderedMagazine.java` has every key,
  and `test/fixtures/ordered-magazine.robot.json` is the smallest robot that uses them.
- **`Servo`** takes `fullTravelSeconds`, how long its whole sweep takes. A goBILDA torque servo
  and an Axon differ by about a factor of two. It also honours `reversed`, the same key the
  motors use.

Files in here are checked by `./tools/test.sh`, so a part with a bad type or a missing source is a
failing test rather than a surprise in the toolbox.

## What can drive what

A mechanism names the device that moves it — `motor` on most of them, `servo` on the two that
aim rather than spin — and not every kind of device may go in every slot. The table is
`ACCEPTS` in `tools/wiring.js`, read by the system cards, the wiring diagram's ghosts, the
filtered part picker and the problems list, so those four cannot disagree in front of a
student. Every refusal is a statement about what **this simulator** can model, not about what
your robot can do:

| Mechanism | May be driven by | Why not the others |
|---|---|---|
| `flywheel` | a motor | a CR servo reports no velocity, so a shooter state machine has nothing to watch, and it carries no torque line or inertia for the spin-up model. Listed absent. |
| `intake`, `feeder` | a motor **or a CR servo** | either is a power that is on or off, which is all these two need |
| `hood`, `claw` | a servo | a CR servo has no position — it is a speed, not an angle — so there is nothing to aim |
| `linearSlide`, `arm`, `turret` | a motor | the load model is a motor's torque–speed line worked against gravity, and no CR servo torque spec exists here to put in its place. Listed absent. |
| a drivetrain wheel | a motor | — |

**A CR servo can run an intake or a feeder and nothing else.** Plenty of teams run an intake off
one and it works here: `SimRobot.powerOf()` reads the driver's applied power out of whichever
table holds the name, and `ProbeCrServo` asserts that a CR-servo intake collects a piece exactly
as a motor intake does and that a CR-servo feeder fires it.

It did not, until this sprint. `collectArtifacts()` and `feedAndShoot()` looked the driver up in
the motor table alone, so naming a CRServo there returned null, the loop skipped, and the intake
never collected — the file loaded, the robot drove, the servo spun, and the artifacts sat on the
floor. Silent, and wrong in the direction of "your code must be broken".

Naming a CR servo on **any other mechanism** is refused by the loader, with the rule and the
reason in the message, rather than loading and quietly never moving.

## What you cannot put here yet

**A battery.** There is no device type for one, because the simulator does not model a battery as
an object — it models a flat 12.4 V supply, which open item 14 already records. So the heaviest
single thing on most robots, and the one whose position moves the centre of mass furthest, cannot
be placed. Its weight goes into `massKg` with the rest of the frame and gets spread out as though
it were part of the chassis.

(A part with no `device` and no `mechanism` — a **structure** — can be placed and weighed, and
does count toward whether the robot fits and how hard it is to turn. What it cannot yet do is
be a battery whose voltage sags.)

That is a real hole in the mass and balance figures and it is written down here rather than papered
over with a part that claims to be a servo.

**A Control Hub or Expansion Hub.** Same reason: a hub is referenced by name in `robot.json` and
has nowhere to record a position, so there is nothing for a part file to write.

## The five wire kinds, and how the diagram draws them

`tools/wiring.js`'s `WIRE_KINDS` is the one table; the diagram, its legend and the table view
all read it, so a wire and its key cannot disagree.

| Kind | Ink | Line | Why |
|---|---|---|---|
| motor | `--accent` | solid | a numbered port, M0 upward |
| servo | `--violet` | solid | a numbered port, S0 upward. A CR servo is on one of these too |
| I2C | `--amber` | solid | the I2C bus. WHICH bus is not modelled |
| USB | `--ink-2` | **dashed** | a USB socket. WHICH socket is not modelled |
| digital / analog | `--ink-2` | **dotted** | a channel or an input. WHICH one is not modelled |

**The last two share an ink on purpose.** They were both `--ink-2` and both solid, so two of
the five legend entries were the same line and a student could not tell a USB camera's wire
from a touch sensor's. The fix is not a fifth palette colour: a fifth colour would say those
two are as different from each other as a motor is from a servo, and they are not. What they
have in common is the more important fact about both — **neither carries a port number**, and
which socket or channel it is on is not modelled here. So they share the ink, which says
"unnumbered", and differ in dash, which says which. A dashed wire in the diagram still means
something else again — *still needed*, in `--red` — and that one is a colour as well as a
pattern for exactly this reason.
