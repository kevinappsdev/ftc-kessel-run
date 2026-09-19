Robots you build in the builder land here.

The ones in `examples/` are hand-written and their comments record which numbers were MEASURED,
which are DERIVED, and which are still ASSUMED — which is the difference between a simulator you
can trust and one you cannot. A form cannot keep that, so the builder opens those but will not
save over them. Save under a new name and it arrives in this directory.

## `builderTree` — how the Build page arranged it

A robot file can carry one key the simulator never reads: `builderTree`, the tree the Build page
draws down its left-hand side. It is written **only when you have actually arranged something** —
made a group, or put the parts in an order of your own — because a flat tree in file order carries
no information and would only be a second copy of the device list that could then disagree with
the first.

A group may carry a `"system"`: which card it came from, one of

`drivetrain` · `shooter` · `intake` · `claw` · `lift` · `arm` · `turret` · `camera` ·
`localization` · `sensors` · `custom`, plus `other` for *Not in a system*.

```jsonc
"builderTree": [
  { "kind": "group", "name": "Shooter", "system": "shooter", "open": true,
    "children": [
      { "kind": "part", "ref": "shooterFlywheel" },
      { "kind": "part", "ref": "shooter" }
    ] }
]
```

**A system is a named group and nothing more.** It is organisation, exactly like the group it sits
on: the simulator never reads it, a group from a card and a group you named yourself are the same
group, and the robot behaves identically with or without any of it. Delete the whole key and you
lose the arrangement and nothing else.

**A robot with no `builderTree` still opens on sensible systems.** The Build page derives them
from what is in the file — drive roles become the Drivetrain, a flywheel with the feeder that
feeds it becomes a Shooter, cameras become Camera, a Pinpoint or an IMU becomes *Where am I*, and
anything left over lands in *Not in a system* where you can see it. That derivation is a **view**:
it is recomputed every time the file is opened and is not written back, so opening a robot to read
one number and saving it does not add twenty lines of grouping you never asked for.
