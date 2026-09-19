package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/*
 * Aim, shoot the preload, leave the tile. An autonomous for
 * examples/decode-advanced-bot.robot.json.
 *
 * THE SHAPE OF A FIRST AUTONOMOUS, and it is worth writing down because it is not obvious:
 *
 *   1. start the slow thing FIRST and let it work while you do something else
 *   2. AIM, because a shot that is not aimed is not a scoring action
 *   3. do the scoring action
 *   4. leave the starting tile, because that is points on its own
 *
 * Step 1 is the one teams miss. The flywheel takes over a second to reach speed. If you spin
 * it up and then wait for it, you have spent a second of a thirty-second period standing
 * still. Start it, AIM WHILE IT CLIMBS, and by the time the robot is pointing the right way
 * the wheel is ready. Nothing here is faster than the physics -- the ORDER is what buys it.
 *
 * HOW IT AIMS. The DECODE goal has an AprilTag mounted beside it, so pointing at the tag is
 * pointing at the goal. The Limelight reports tx: how many degrees left or right of the
 * camera centre the target is. Turn until tx is about zero and the robot is lined up.
 *
 * That is the same proportional control loop as VisionTagAuto in examples/vision-teamcode --
 * measure the error, turn in proportion to it, stop inside a tolerance -- and it is worth
 * recognising the pattern, because you will write it again for every mechanism that has a
 * position to reach.
 *
 * COUNTING SHOTS BY WATCHING THE FLYWHEEL. There is no sensor that says "a ball left". What
 * there is, is a velocity that DIPS each time a ball takes energy out of the wheel, and that
 * dip is a better shot counter than a timer because it measures what actually happened. If a
 * ball jams, the dip never comes, the count never rises, and the timeout below is what stops
 * the robot standing there feeding an empty magazine for the rest of the match.
 *
 * ALWAYS PUT A TIMEOUT ON A LOOP THAT WAITS FOR A SENSOR. The failure you are guarding
 * against is not the sensor being wrong; it is the thing you are waiting for never happening.
 *
 * WHAT IT ACTUALLY DOES, AND THE BIT LEFT FOR YOU
 * ----------------------------------------------
 * Run from the default start pose on the DECODE field, this aims correctly -- it turns from
 * 90 degrees to about 107.5, which is straight at the Blue goal -- fires all three, and puts
 * TWO of them in. Not three.
 *
 * That is left honest rather than tuned away, because the third shot is the most useful
 * exercise in this folder. The numbers below were found by trying values and watching the
 * result, which is exactly what you will do on a real robot, and they got as far as two.
 *
 * Things worth trying, roughly in order of how much they are likely to teach:
 *
 *   - RE-AIM BETWEEN SHOTS. The robot aims once and then fires three times. Firing pushes the
 *     robot a little, so shot three leaves from a slightly different heading than shot one.
 *     Calling aimAtGoal() inside the firing loop is a four-line change.
 *   - USE THE RANGE. The Limelight reports how far away the target is. A real robot looks the
 *     flywheel speed and the hood position up in a table indexed by distance, instead of using
 *     one setting for every shot the way this file does.
 *   - TIGHTEN AIM_TOLERANCE_DEG, and see where it stops helping and starts costing time.
 *   - WAIT LONGER AFTER EACH SHOT. READY_TOLERANCE_TICKS below decides how recovered the
 *     flywheel has to be before the next ball is fed. Read the note on it first: it is the
 *     reason this scores two rather than one.
 *
 * A sample that scores everything teaches nothing about why it scores. This one has a
 * specific, reproducible gap in it, and closing it is the job.
 *
 * TO RUN IT
 *   ./run.sh examples/decode-advanced-teamcode --robot examples/decode-advanced-bot.robot.json --field examples/decode.field.json
 *
 * Pick a start pose with the "Place robot" control under the field -- the robot declares four.
 */
@Autonomous(name = "Advanced: Shoot and Leave", group = "Advanced")
public class AdvancedBotAuto extends LinearOpMode {

    private DcMotor leftFront, leftRear, rightRear, rightFront;
    private DcMotorEx shooter;
    private DcMotor intake, indexer;
    private Servo hood;
    private GoBildaPinpointDriver pinpoint;
    private Limelight3A limelight;

    private static final double SHOOTER_TICKS_PER_SEC = 1200.0;

    /*
     * HOW FAR THE FLYWHEEL DROPS when a ball goes through, in ticks per second. Measured on a
     * real robot. Watching for this dip is how a robot with no sensor in the barrel knows a
     * ball actually left.
     */
    private static final double SHOT_DIP_TICKS = 80.0;

    /*
     * HOW CLOSE TO TARGET COUNTS AS READY TO FIRE -- and this must be a good deal TIGHTER
     * than the dip above.
     *
     * Getting this wrong is subtle and expensive. If "ready" means "within one dip of target"
     * then a wheel that has just lost a dip's worth of speed is immediately ready again, so
     * the next ball is fed while the wheel is still slow, and it lands short. The symptom is
     * an autonomous where the first shot scores and the rest do not -- which looks like a
     * hardware problem and is not.
     */
    private static final double READY_TOLERANCE_TICKS = 15.0;

    /* The robot declares a preload of three, so three is what there is to fire. */
    private static final int SHOTS_WANTED = 3;

    /*
     * Hood position for this shot. 0.0 is 57.7 deg and 1.0 is 46.3, so a HIGHER number is a
     * FLATTER shot -- the opposite of what most people assume, and worth checking on your own
     * robot before you trust a table of these.
     *
     * One value for every shot is a simplification. This robot's own shot table, in
     * examples/decode-advanced-bot.robot.json, carries eleven rows of (distance, speed, hood)
     * because the right hood angle depends on how far away you are.
     */
    private static final double HOOD_FOR_THIS_SHOT = 0.50;

    /*
     * AIMING, with the same three constants VisionTagAuto explains at length: a proportional
     * gain, a floor that beats stiction, and a cap that keeps the turn controllable.
     */
    private static final double AIM_GAIN = 0.020;
    private static final double AIM_MIN_POWER = 0.10;
    private static final double AIM_MAX_POWER = 0.30;
    private static final double AIM_TOLERANCE_DEG = 1.0;
    private static final double AIM_TIMEOUT_S = 4.0;

    private static final double SPIN_UP_TIMEOUT_S = 3.0;
    private static final double FIRING_TIMEOUT_S  = 6.0;

    /* How far to come off the tile, in inches, measured by the odometry pods. */
    private static final double LEAVE_DISTANCE_IN = 26.0;
    private static final double LEAVE_POWER = 0.4;
    private static final double LEAVE_TIMEOUT_S = 4.0;

    @Override
    public void runOpMode() {
        leftFront  = hardwareMap.get(DcMotor.class, "leftFront");
        leftRear   = hardwareMap.get(DcMotor.class, "leftRear");
        rightRear  = hardwareMap.get(DcMotor.class, "rightRear");
        rightFront = hardwareMap.get(DcMotor.class, "rightFront");

        shooter = hardwareMap.get(DcMotorEx.class, "shooter");
        intake  = hardwareMap.get(DcMotor.class, "intake");
        indexer = hardwareMap.get(DcMotor.class, "indexer");
        hood    = hardwareMap.get(Servo.class, "hood");

        pinpoint  = hardwareMap.get(GoBildaPinpointDriver.class, "pinpoint");
        limelight = hardwareMap.get(Limelight3A.class, "limelight");

        // Pipeline 0 on this robot is the AprilTag one, and the camera has to be started.
        limelight.pipelineSwitch(0);
        limelight.start();

        leftFront.setDirection(DcMotor.Direction.REVERSE);
        leftRear.setDirection(DcMotor.Direction.REVERSE);
        rightRear.setDirection(DcMotor.Direction.FORWARD);
        rightFront.setDirection(DcMotor.Direction.FORWARD);

        for (DcMotor m : new DcMotor[] { leftFront, leftRear, rightRear, rightFront }) {
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        shooter.setMode(DcMotor.RunMode.RUN_USING_ENCODER);

        telemetry.addData("Status", "Ready. Press Start.");
        telemetry.update();

        waitForStart();
        if (!opModeIsActive()) return;

        // 1 -- the slow thing first, and the hood set while it climbs.
        shooter.setVelocity(SHOOTER_TICKS_PER_SEC);
        hood.setPosition(HOOD_FOR_THIS_SHOT);

        // 2 -- aim, WHILE the flywheel spins up. That overlap is the whole reason the
        // flywheel was started first.
        boolean aimed = aimAtGoal();

        // 3 -- fire, once the wheel really is up to speed.
        boolean ready = waitForFlywheel();
        int fired = ready ? fireUntil(SHOTS_WANTED) : 0;

        shooter.setVelocity(0);
        indexer.setPower(0);

        // 4 -- leave the tile. Worth doing even if the shooting went badly: a robot that
        // scored nothing AND sat on its tile has had a worse match than it needed to.
        double travelled = leaveTheTile();

        telemetry.addData("Result", "%s, %d of %d shots, moved %.1f in",
                aimed ? "aimed" : "NOT aimed", fired, SHOTS_WANTED, travelled);
        telemetry.update();
    }

    /*
     * Turn until the goal's AprilTag is straight ahead. True if it got there.
     *
     * tx is degrees off centre, positive to the RIGHT of the crosshair, so the robot has to
     * turn right when tx is positive -- which is why the power is negated against the error.
     * Getting that sign wrong gives a robot that turns confidently AWAY from the target,
     * which is the most common vision bug there is.
     */
    private boolean aimAtGoal() {
        double deadline = getRuntime() + AIM_TIMEOUT_S;

        while (opModeIsActive() && getRuntime() < deadline) {
            LLResult result = limelight.getLatestResult();

            // ALWAYS CHECK isValid(). A result with no target still arrives, with tx at zero
            // -- which looks exactly like "perfectly aimed" to code that skips this line.
            if (result == null || !result.isValid()) {
                telemetry.addData("Aiming", "no tag in view");
                telemetry.update();
                continue;
            }

            double error = result.getTx();
            if (Math.abs(error) <= AIM_TOLERANCE_DEG) {
                setTurnPower(0);
                telemetry.addData("Aiming", "on target");
                telemetry.update();
                return true;
            }

            setTurnPower(clamp(-AIM_GAIN * error, AIM_MIN_POWER, AIM_MAX_POWER));
            telemetry.addData("Aiming", "tx %.1f deg", error);
            telemetry.update();
        }

        setTurnPower(0);
        return false;
    }

    /* Positive turns one way, negative the other. */
    private void setTurnPower(double power) {
        leftFront.setPower(-power);
        leftRear.setPower(-power);
        rightRear.setPower(power);
        rightFront.setPower(power);
    }

    /* Keep the sign, force the magnitude between a floor and a ceiling. */
    private static double clamp(double value, double min, double max) {
        double magnitude = Math.min(Math.max(Math.abs(value), min), max);
        return Math.signum(value) * magnitude;
    }

    /* Wait for the wheel to reach speed, or come back to it after a shot. */
    private boolean waitForFlywheel() {
        double deadline = getRuntime() + SPIN_UP_TIMEOUT_S;
        while (opModeIsActive() && getRuntime() < deadline) {
            double velocity = shooter.getVelocity();
            if (velocity >= SHOOTER_TICKS_PER_SEC - READY_TOLERANCE_TICKS) return true;
            telemetry.addData("Spinning up", "%.0f / %.0f", velocity, SHOOTER_TICKS_PER_SEC);
            telemetry.update();
        }
        return false;
    }

    /*
     * Fire this many balls, ONE AT A TIME, and return how many actually left.
     *
     * THE LOOP IS PER SHOT, AND THAT IS THE WHOLE LESSON. The obvious version -- switch the
     * indexer on, count dips until you have three, switch it off -- is shorter, and it scores
     * one shot out of three.
     *
     * Here is why. Every ball that goes through steals energy from the flywheel, so the wheel
     * is SLOWER straight after a shot than it was before. A slower wheel throws a shorter
     * distance. So with the indexer held on, ball one leaves at the speed you tuned for and
     * goes in; balls two and three leave during the recovery, at whatever speed the wheel
     * happens to be passing through, and land short. Nothing errors, the count still reaches
     * three, and two thirds of your autonomous quietly misses.
     *
     * So: wait for the wheel, feed until one ball leaves, stop feeding, wait for the wheel to
     * come back, repeat. It is slower by the recovery time, and the recovery time is not
     * optional -- it is how long the shot actually takes.
     *
     * This is what every good shooter state machine on a real robot is doing, and it is why
     * they are built around the flywheel's measured velocity rather than around a timer.
     */
    private int fireUntil(int wanted) {
        int shots = 0;
        double deadline = getRuntime() + FIRING_TIMEOUT_S;

        for (int i = 0; i < wanted && opModeIsActive() && getRuntime() < deadline; i++) {
            // Back up to speed before feeding the next one.
            if (!waitForFlywheel()) break;

            // A little intake helps unstick a ball sitting awkwardly against the indexer.
            indexer.setPower(1.0);
            intake.setPower(0.3);

            boolean left = waitForOneShot(deadline);

            indexer.setPower(0);
            intake.setPower(0);

            if (!left) break;
            shots++;
            telemetry.addData("Firing", "%d of %d away", shots, wanted);
            telemetry.update();
        }

        indexer.setPower(0);
        intake.setPower(0);
        return shots;
    }

    /*
     * Watch the flywheel until its velocity DIPS, which is one ball leaving.
     *
     * Comparing each reading with the PREVIOUS one, rather than with the target, is what makes
     * this detect a single event instead of reporting true for the whole of a long recovery.
     */
    private boolean waitForOneShot(double deadline) {
        double last = shooter.getVelocity();
        while (opModeIsActive() && getRuntime() < deadline) {
            double velocity = shooter.getVelocity();
            if (last - velocity > SHOT_DIP_TICKS) return true;
            last = velocity;

            telemetry.addData("Firing", "flywheel %.0f, waiting for the dip", velocity);
            telemetry.update();
        }
        return false;
    }

    /*
     * Drive forward off the starting tile, measuring with the odometry pods.
     *
     * WHY ODOMETRY RATHER THAN THE DRIVE ENCODERS. A drive encoder counts how far the WHEEL
     * turned, which is the distance travelled only if the wheel never slipped. An odometry pod
     * is an unpowered wheel measuring the floor going past, so it cannot spin up under
     * torque -- which is exactly what the drive wheels do in the first moment of hard
     * acceleration, and exactly when this measurement starts.
     */
    private double leaveTheTile() {
        pinpoint.update();
        double startX = pinpoint.getPosX(DistanceUnit.INCH);
        double startY = pinpoint.getPosY(DistanceUnit.INCH);
        double deadline = getRuntime() + LEAVE_TIMEOUT_S;
        double travelled = 0;

        setDrivePower(LEAVE_POWER);
        while (opModeIsActive() && travelled < LEAVE_DISTANCE_IN && getRuntime() < deadline) {
            pinpoint.update();
            double dx = pinpoint.getPosX(DistanceUnit.INCH) - startX;
            double dy = pinpoint.getPosY(DistanceUnit.INCH) - startY;

            // Straight-line distance from where we started, so this measures how far the
            // robot GOT rather than how far it drove -- the number the rule is about.
            travelled = Math.hypot(dx, dy);

            telemetry.addData("Leaving", "%.1f of %.1f in", travelled, LEAVE_DISTANCE_IN);
            telemetry.update();
        }
        setDrivePower(0);
        return travelled;
    }

    private void setDrivePower(double power) {
        leftFront.setPower(power);
        leftRear.setPower(power);
        rightRear.setPower(power);
        rightFront.setPower(power);
    }
}
