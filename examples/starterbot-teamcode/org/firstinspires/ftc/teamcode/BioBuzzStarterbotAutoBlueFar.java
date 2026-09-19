/*
 * A worked BIOBUZZ autonomous for the goBILDA StarterBot.
 *
 * Written to be READ. Every wait and every heading says why it is the number it is, because the
 * point of a worked example is not that it scores -- it is that you can see where each number
 * came from and change one of them on purpose.
 *
 * WHAT IT DOES, AND WHAT THAT IS WORTH
 * ------------------------------------
 *   LEAVE          3 points. Manual 10.5.4: get off the perimeter wall.
 *   HIVE TIP      20 points. Three POLLEN into the upward CELL joins the three NECTAR already
 *                  staged there (10.3.1 B.i), and six is what tips it.
 *   PARK           5 points. Manual 10.5.4: finish at least partly in your LOADING ZONE.
 *
 * WHERE IT STANDS TO SHOOT, WHICH IS THE WHOLE TRICK
 * --------------------------------------------------
 * This robot has NO HOOD. The launcher is a fixed wheel at a fixed angle, so the only variable a
 * driver or an autonomous has is WHERE IT STANDS. Fire from the wrong distance and the ball flies
 * over the CELL or falls short of it, and no amount of tuning in code changes that.
 *
 * The blue upward CELL is the one on the FAR side of the blue HIVE, and its mouth -- a 20 by 14
 * in opening, 59 in up -- faces the far wall, 30 degrees above level. So it can only be scored
 * from the far half of the field, which is where this robot starts. (The other cell, the one
 * that points at the audience, is the one tilted DOWN; a shot at it from here hits its back.)
 *
 * Measured, in the simulator, by firing from every pose in that quadrant: at the launch angle
 * this robot is modelled with, a POLLEN goes in from about 48 inches out to the far corner. The
 * Blue Far start pose is 59 inches from the mouth -- inside that band -- WHICH IS WHY THIS
 * AUTONOMOUS SHOOTS FROM ALMOST WHERE IT STARTS. It backs off the wall far enough to score LEAVE
 * and no further.
 *
 * If you correct the launch angle in biobuzz-starterbot.robot.json -- and you should, with a
 * protractor against the real chute -- that band moves, and this routine has to move with it.
 * ProbeStarterBotAuto will tell you: it asserts the points, so it fails with the score rather
 * than with a stack trace.
 *
 * WHY TIME AND NOT ENCODERS
 * -------------------------
 * Deliberate. An encoder-driven autonomous is better and it is also twice the code, and the thing
 * worth showing first is the SHAPE of a routine: leave, aim, spin up, feed, park. Everything here
 * is one `sleep` away from being a `while (motor.isBusy())`.
 */
package org.firstinspires.ftc.teamcode;

import static com.qualcomm.robotcore.hardware.DcMotor.ZeroPowerBehavior.BRAKE;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

@Autonomous(name = "BioBuzz StarterBot Auto (Blue Far)", group = "StarterBot")
public class BioBuzzStarterbotAutoBlueFar extends LinearOpMode {

    private DcMotor leftFrontDrive, leftBackDrive, rightFrontDrive, rightBackDrive;
    private DcMotorEx launcher;
    private DcMotor intake;
    private CRServo windmillServo;
    private IMU imu;

    /*
     * goBILDA's own launcher numbers, from BioBuzzStarterbotTeleopMecanum. Kept identical on
     * purpose: an autonomous that spun the wheel to a different speed than the TeleOp would score
     * from a different distance, and then nothing a driver learned in practice would transfer.
     *
     * 1250 ticks/s on the 1:1 motor the kit builds (assembly STEP 38 removes the gearbox) is
     * 2678 rpm, which is what their comment says.
     */
    static final int LAUNCHER_TARGET_VELOCITY = 1250;
    /**
     * goBILDA's own feed gate, kept here for reference and deliberately NOT used as one.
     *
     * Their TeleOp feeds as soon as the wheel passes this. See the note in the feed step for why
     * an autonomous waits for the full target instead.
     */
    static final int LAUNCHER_MIN_VELOCITY = 1200;

    @Override
    public void runOpMode() {
        leftFrontDrive  = hardwareMap.get(DcMotor.class, "left_front_drive");
        leftBackDrive   = hardwareMap.get(DcMotor.class, "left_back_drive");
        rightFrontDrive = hardwareMap.get(DcMotor.class, "right_front_drive");
        rightBackDrive  = hardwareMap.get(DcMotor.class, "right_back_drive");
        launcher        = hardwareMap.get(DcMotorEx.class, "launcher");
        intake          = hardwareMap.get(DcMotor.class, "intake");
        windmillServo   = hardwareMap.get(CRServo.class, "windmillServo");
        imu             = hardwareMap.get(IMU.class, "imu");

        // The hub is bolted logo-up with the USB port forward. Get this wrong and every heading
        // the routine reads is wrong in a way that looks like a drivetrain problem.
        imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(
                RevHubOrientationOnRobot.LogoFacingDirection.UP,
                RevHubOrientationOnRobot.UsbFacingDirection.FORWARD)));
        imu.resetYaw();

        // The same directions the TeleOp sets. Get one of these wrong and the robot turns when
        // you ask it to drive -- and it will do it identically every time, which is what makes
        // it look like a code bug rather than a wiring one.
        leftFrontDrive.setDirection(DcMotor.Direction.REVERSE);
        leftBackDrive.setDirection(DcMotor.Direction.REVERSE);
        rightFrontDrive.setDirection(DcMotor.Direction.FORWARD);
        rightBackDrive.setDirection(DcMotor.Direction.FORWARD);
        windmillServo.setDirection(DcMotorSimple.Direction.REVERSE);

        for (DcMotor m : new DcMotor[] { leftFrontDrive, leftBackDrive,
                                         rightFrontDrive, rightBackDrive }) {
            m.setZeroPowerBehavior(BRAKE);
        }

        /*
         * THESE COEFFICIENTS ARE NOT OPTIONAL, and they are the least obvious line in the file.
         *
         * With the SDK's defaults this flywheel never settles -- it swings between roughly 740
         * and 1600 ticks/s around a 1250 target and stays there, because a 1:1 motor carrying a
         * 100 g wheel has almost no inertia to damp the loop. Every shot then leaves at whatever
         * speed the oscillation happened to be at, and lands somewhere different.
         *
         * Straight out of goBILDA's TeleOp, for the same reason it is in theirs.
         */
        launcher.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        launcher.setPIDFCoefficients(DcMotor.RunMode.RUN_USING_ENCODER,
                new PIDFCoefficients(40, 0, 0, 12.5));

        telemetry.addData("Ready", "Blue Far. 4 POLLEN preloaded.");
        telemetry.update();

        waitForStart();
        if (isStopRequested()) return;

        // ---- 1. LEAVE, AND STOP ON THE SWEET SPOT -------------------------------------------
        //
        // Manual 10.5.4: LEAVE wants the ROBOT no longer contacting the perimeter wall, assessed
        // at the end of AUTO (10.5 F). Three points for five inches of driving.
        //
        // FIVE INCHES AND NOT MORE. This robot has no hood, so the only way to change where a
        // ball lands is to change where the robot stands -- and firing from every reachable spot
        // in the far quadrant, aimed at the mouth of the blue upward CELL, says it goes in from
        // about 48 in out to the far corner:
        //
        //     (120, 135) start,  59 in    3 of 3 in, tips
        //     (120, 129.4)       55 in    3 of 3 in, tips     <- here
        //     (120, 124)         51 in    3 of 3 in, tips
        //     (116, 124)         48 in    3 of 3 in, tips
        //     (120, 120)         48 in    0 of 4               the near edge of the band
        //
        // The start pose would already score, but it is against the wall and LEAVE wants it off
        // one, so this drives five and a half inches closer and stops: 55 in from the mouth,
        // with 7 in of band still in hand on the near side. THREE IS THE NUMBER THAT MATTERS:
        // the upward CELL already holds three NECTAR, and three more elements is six, which is
        // what tips the HIVE. The fourth ball meets the cell that has just swung down and comes
        // off it, which is the price of firing after the tip.
        //
        // MEASURED, not calculated: 0.35 power for 0.35 s moves this robot 7.0 in and for 1.0 s
        // moves it 22.4, so it settles at 23.7 in/s after about 1.3 in of getting there. 5.6 in
        // wants 0.29 s. Working it out from the 1.0 s figure alone would have asked for 0.25 and
        // stopped three inches short.
        drive(0.35, 0.29);
        sleep(150);

        // ---- 2. Aim -------------------------------------------------------------------------
        //
        // Now about (120, 129.4), and the mouth of the blue upward CELL is at (84.5, 88.0), 59
        // in up and facing this way. That bearing is -130.6 degrees; the robot starts pointing
        // -90 and resetYaw() called that zero, so this is 40.6 degrees clockwise.
        //
        // NOT THE CELL AT (85, 56). That is the other end of the same hive, the one pointing at
        // the audience -- and it is pointing DOWN. This routine once aimed there, and scored,
        // because the simulator let the ball through the far cell on its way; it does not now.
        //
        // TURNED BY READING THE HEADING, not by running a motor for a measured time. A timed turn
        // is a different angle on a fresh battery than on a flat one, and it is the most common
        // reason an autonomous that worked in the pits misses at a competition. It matters more
        // than usual here: a fixed-angle launcher cannot correct for a bad heading, and at this
        // range one degree of aim error is an inch sideways at a mouth 20 in wide.
        turnTo(-40.6, 2.5);

        // ---- 3. Spin up, and WAIT FOR THE TARGET, NOT FOR goBILDA'S MINIMUM -------------------
        //
        // The single most valuable line in the routine.
        //
        // Their launch() feeds as soon as the wheel passes LAUNCHER_MIN_VELOCITY, 1200 ticks/s,
        // and for a DRIVER that is right: a slightly slow shot is still a shot and waiting is
        // time they do not have. An autonomous is a different problem. Firing the moment the
        // wheel crosses 1200 -- when it settles at 1278 -- throws every ball about 6 per cent
        // slow, which at this range lands it a foot short, and the routine looks perfectly aimed
        // and scores nothing. That is exactly what this one did until the wheel speed was printed
        // at the instant it fired. The cost of waiting is about a tenth of a second.
        launcher.setVelocity(LAUNCHER_TARGET_VELOCITY);
        waitForFlywheel(2.0);

        // ---- 4. Feed, ONE AT A TIME -------------------------------------------------------
        //
        // Four preloaded POLLEN (10.3.1 A.iv). The upward CELL already holds three NECTAR, so the
        // THIRD of these makes six and TIPS THE HIVE -- 20 points, Table 10-2.
        //
        // ONE BALL, THEN WAIT FOR THE WHEEL, THEN THE NEXT. Holding the windmill on empties the
        // hopper in a second, and the flywheel cannot keep up: each shot takes energy out of it,
        // and the second and third leave slow and land short. Dumping all four scored two or
        // three depending on a few milliseconds of timing -- reproducible, and far too close to
        // the edge to be an autonomous anybody should rely on.
        //
        // Pulsing costs about a second and a half of a thirty-second period and puts all four in.
        // It is also what every good shooter state machine on a real robot does: it watches
        // the flywheel velocity for the dip that says a ball left, and will not feed again until
        // it has recovered.
        //
        // THE INTAKE STAYS OFF, and that cost a ball to find out. goBILDA's TeleOp adds 0.5 to it
        // while launching -- "this can sometimes help dislodge stuck elements" -- which for a
        // driver is a fair trade. In here it is not: the intake is a 50.9:1 motor pulling current,
        // the pack sags, the velocity loop does not get all the way back between shots, and the
        // marginal shot falls short.
        for (int ball = 0; ball < 4 && opModeIsActive(); ball++) {
            waitForFlywheel(1.5);
            windmillServo.setPower(1.0);
            sleep(280);                 // long enough for the windmill to push exactly one
            windmillServo.setPower(0);
            sleep(120);                 // let the wheel start recovering before asking again
        }
        launcher.setVelocity(0);

        // ---- 5. PARK ---------------------------------------------------------------------------
        //
        // Manual 10.5.4: at least partly in the LOADING ZONE. Blue's is the strip against the far
        // side wall, x 131 to 142 and y 25 to 48 in. Aiming for about (136, 37): inside the zone,
        // and far enough off the wall that an 18 in robot fits.
        //
        // From (120, 130) that is 94 in at a bearing of -80 degrees, which is +10 in the frame
        // resetYaw() established.
        turnTo(9.8, 2.5);
        drive(0.4, 4.1);

        // It is also where a human player hands NECTAR in during TeleOp (G427), so it is where
        // you want to be at the buzzer anyway.
        telemetry.addData("Done", "LEAVE + TIP + PARK");
        telemetry.update();
    }

    //--------------------------------------------------------------------------------------------

    /** Straight, for a while. Positive is forwards. */
    private void drive(double power, double seconds) {
        setDrive(power, power, power, power);
        sleep((long) (seconds * 1000));
        setDrive(0, 0, 0, 0);
    }

    /** On the spot. POSITIVE POWER IS CLOCKWISE, measured on this robot at about 89 deg/s. */
    private void spin(double power) {
        setDrive(power, -power, power, -power);
    }

    /** The heading now, in degrees, in the frame resetYaw() established. */
    private double heading() {
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES);
    }

    /**
     * Turns until the heading is what was asked for, or until patience runs out.
     *
     * Proportional, and slow near the end: a bang-bang turn overshoots by however far the robot
     * coasts, which on a braked mecanum base is a few degrees and is exactly the error that puts
     * a fixed-angle shot beside the CELL instead of in it.
     *
     * The 0.6 degree tolerance is deliberate rather than 0 -- asking a real drivetrain for an exact
     * heading is asking it to hunt forever. It is 1 and not 3 because a fixed-angle launcher has
     * no way to correct for a bad heading: at this range every degree of aim error is an inch and
     * a third sideways at the CELL.
     */
    private void turnTo(double targetDeg, double timeoutSeconds) {
        double deadline = getRuntime() + timeoutSeconds;

        // SEVERAL PASSES, BECAUSE STOPPING IS NOT INSTANT. Braked mecanum wheels still coast about
        // three degrees after the power goes off, so a single pass that stops the moment it is
        // inside tolerance settles OUTSIDE it -- this routine aimed at -27 and came to rest at
        // -29.8 until the recheck existed. Each pass is shorter than the last because the error
        // it is correcting is smaller.
        for (int pass = 0; pass < 5 && opModeIsActive() && getRuntime() < deadline; pass++) {
            if (Math.abs(errorTo(targetDeg)) < 0.6) break;
            while (opModeIsActive() && getRuntime() < deadline) {
                double error = errorTo(targetDeg);
                if (Math.abs(error) < 0.6) break;
                // Clockwise is positive power and a NEGATIVE error, hence the sign.
                // The floor is 0.07 and not 0.12 because what limits the final accuracy is how
                // far the robot COASTS after the power goes off, and that scales with the speed
                // it was turning at. A higher floor makes the last nudge overshoot and the
                // passes below just oscillate around the target -- which they did, at +/-1.4
                // degrees, which is two inches sideways at the CELL.
                double power = -Math.signum(error) * Math.max(0.07, Math.min(0.4,
                        Math.abs(error) / 90.0 * 0.4));
                spin(power);
                telemetry.addData("Turning", "%.1f -> %.1f", heading(), targetDeg);
                telemetry.update();
                sleep(10);
            }
            setDrive(0, 0, 0, 0);
            sleep(120);          // let it stop before asking again
        }
    }

    /** How far, and which way, the robot still has to turn. Wrapped to +/-180. */
    private double errorTo(double targetDeg) {
        double error = targetDeg - heading();
        while (error > 180) error -= 360;
        while (error < -180) error += 360;
        return error;
    }

    private void setDrive(double lf, double rf, double lb, double rb) {
        leftFrontDrive.setPower(lf);
        rightFrontDrive.setPower(rf);
        leftBackDrive.setPower(lb);
        rightBackDrive.setPower(rb);
    }

    /** Blocks until the flywheel is fast enough to throw, or until patience runs out. */
    private void waitForFlywheel(double timeoutSeconds) {
        double deadline = getRuntime() + timeoutSeconds;
        while (opModeIsActive()
                && launcher.getVelocity() < LAUNCHER_TARGET_VELOCITY
                && getRuntime() < deadline) {
            telemetry.addData("Flywheel", "%.0f / %d", launcher.getVelocity(),
                              LAUNCHER_TARGET_VELOCITY);
            telemetry.update();
            sleep(20);
        }
    }
}
