package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.IMU;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;

/*
 * An autonomous for examples/tank-starter.robot.json: drive off the line, then turn to face a
 * heading.
 *
 * THE TWO WAYS TO MAKE A ROBOT GO A DISTANCE, and why this file uses both.
 *
 *   BY ENCODER   count the wheel's own rotations. Repeatable, and it does not care about the
 *                battery: a tired battery makes the robot slower, not shorter.
 *   BY IMU       read the angle the robot is actually pointing. The only honest way to turn,
 *                because a turn by encoder assumes the wheels never slip, and turning is
 *                exactly when they do.
 *
 * A THIRD WAY, WHICH YOU WILL SEE EVERYWHERE, is by time: full power for 1.2 seconds. It is
 * the easiest to write and the first thing to break, because 1.2 seconds at the start of a
 * match and 1.2 seconds on the fourth run of the day are different distances. Use it for a
 * mechanism that hits a hard stop; do not use it to place a robot.
 *
 * WHAT THIS DOES
 *   1. drives forward 24 inches, off the starting tile
 *   2. turns to face 90 degrees
 *   3. stops
 *
 * It is the shape of nearly every first autonomous: leave, do one thing, stop somewhere
 * predictable. Get this working before you write anything longer.
 *
 * TO RUN IT
 *   ./run.sh examples/tank-teamcode --robot examples/tank-starter.robot.json
 */
@Autonomous(name = "Tank: Drive and Turn", group = "Tank")
public class TankAuto extends LinearOpMode {

    private DcMotor leftDrive;
    private DcMotor rightDrive;
    private IMU imu;

    // COUNTS PER INCH, and where it comes from. The motor's encoder reports a fixed number of
    // ticks per turn of its output shaft; the wheel travels its own circumference per turn.
    // So ticks per inch is (ticks per motor revolution x gear ratio) / (wheel circumference).
    //
    // 537.7 is the goBILDA 5202/5203 312 RPM gearbox, which is what tank-starter.robot.json
    // declares. The wheel is 96 mm, which is 3.78 in, so its circumference is 11.87 in.
    //
    // MEASURE THIS ON YOUR ROBOT rather than trusting it. Mark the floor, drive 48 inches,
    // measure what you actually got, and scale. Gear ratios and wheel wear both move it.
    private static final double TICKS_PER_MOTOR_REV = 537.7;
    private static final double WHEEL_CIRCUMFERENCE_IN = 3.78 * Math.PI;
    private static final double TICKS_PER_INCH = TICKS_PER_MOTOR_REV / WHEEL_CIRCUMFERENCE_IN;

    private static final double DRIVE_POWER = 0.4;
    private static final double TURN_POWER  = 0.3;

    // How close to the target heading counts as arrived. Too tight and the robot hunts back
    // and forth forever; 2 degrees is comfortably inside what a drivetrain can hold.
    private static final double HEADING_TOLERANCE_DEG = 2.0;

    @Override
    public void runOpMode() {
        leftDrive  = hardwareMap.get(DcMotor.class, "left_drive");
        rightDrive = hardwareMap.get(DcMotor.class, "right_drive");
        imu        = hardwareMap.get(IMU.class, "imu");

        leftDrive.setDirection(DcMotor.Direction.REVERSE);
        rightDrive.setDirection(DcMotor.Direction.FORWARD);
        leftDrive.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightDrive.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        telemetry.addData("Status", "Ready. Press Start.");
        telemetry.update();

        // EVERYTHING ABOVE HAPPENS AT INIT. Nothing below runs until the driver presses Start,
        // which is what waitForStart() is for. Putting motion above this line is how a robot
        // moves during the init phase, which is a foul.
        waitForStart();

        if (opModeIsActive()) {
            driveInches(24.0);
            turnToHeading(90.0);

            telemetry.addData("Status", "Done");
            telemetry.update();
        }
    }

    /* Drive straight, by the drive encoders. Positive is forwards. */
    private void driveInches(double inches) {
        int ticks = (int) Math.round(inches * TICKS_PER_INCH);

        // STOP_AND_RESET_ENCODER zeroes the counts, so the target below is measured from HERE
        // rather than from wherever the robot happened to be when it was switched on.
        leftDrive.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        rightDrive.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        leftDrive.setTargetPosition(ticks);
        rightDrive.setTargetPosition(ticks);
        leftDrive.setMode(DcMotor.RunMode.RUN_TO_POSITION);
        rightDrive.setMode(DcMotor.RunMode.RUN_TO_POSITION);

        leftDrive.setPower(DRIVE_POWER);
        rightDrive.setPower(DRIVE_POWER);

        // NOTE THE && , not ||. Waiting for BOTH motors to finish sounds more careful and is
        // worse: if one side arrives first and the other stalls half an inch short, the loop
        // never ends and the autonomous is over. Stopping when EITHER is done keeps the robot
        // honest about time, which is the scarcer resource.
        while (opModeIsActive() && leftDrive.isBusy() && rightDrive.isBusy()) {
            telemetry.addData("Driving", "%.1f of %.1f in",
                    leftDrive.getCurrentPosition() / TICKS_PER_INCH, inches);
            telemetry.update();
        }

        leftDrive.setPower(0);
        rightDrive.setPower(0);
        leftDrive.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        rightDrive.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
    }

    /* Turn on the spot until the IMU says the robot faces this heading, in field degrees. */
    private void turnToHeading(double targetDeg) {
        double error = angleDifference(targetDeg, currentHeadingDeg());

        while (opModeIsActive() && Math.abs(error) > HEADING_TOLERANCE_DEG) {
            // Sign only, no proportional term: this turns at one speed and stops when it
            // arrives. It is the simplest thing that works, and it overshoots a little. When
            // that starts costing you points, scale the power by the error -- slow down as
            // you approach -- which is the "P" in a PID controller and about two more lines.
            double power = Math.signum(error) * TURN_POWER;
            leftDrive.setPower(-power);
            rightDrive.setPower(power);

            telemetry.addData("Turning", "at %.1f, want %.1f, off by %.1f",
                    currentHeadingDeg(), targetDeg, error);
            telemetry.update();

            error = angleDifference(targetDeg, currentHeadingDeg());
        }

        leftDrive.setPower(0);
        rightDrive.setPower(0);
    }

    private double currentHeadingDeg() {
        return imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES);
    }

    /*
     * The shortest way round from one angle to another, in degrees, as a number between -180
     * and +180.
     *
     * WHY THIS EXISTS. Subtracting two headings is wrong at the wrap-around: going from 170
     * degrees to -170 degrees is a 20 degree turn, but the subtraction says 340. Without this,
     * a robot near the wrap point turns the long way round -- most of a full circle -- for no
     * reason a student watching it could possibly guess.
     */
    private static double angleDifference(double targetDeg, double currentDeg) {
        double diff = (targetDeg - currentDeg) % 360.0;
        if (diff > 180.0)  diff -= 360.0;
        if (diff < -180.0) diff += 360.0;
        return diff;
    }
}
