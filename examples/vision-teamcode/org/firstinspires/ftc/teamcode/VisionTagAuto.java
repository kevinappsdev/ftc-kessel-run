package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;

import java.util.List;

/*
 * Turn until an AprilTag is straight ahead, then drive off the starting tile.
 *
 * THIS IS A CONTROL LOOP, which is the idea worth taking away from this file. It is three
 * steps, and every aiming routine any team writes is the same three:
 *
 *   1. measure the error   -- how far off am I? (the tag's bearing, in degrees)
 *   2. act in proportion   -- turn harder when the error is big, gently when it is small
 *   3. stop when close     -- inside a tolerance you chose on purpose
 *
 * Step 2 is what makes it a PROPORTIONAL controller, the "P" of PID, and it is most of what
 * PID is worth. Turning at one fixed speed until you arrive -- which TankAuto in the tank
 * example does, on purpose, because it is simpler -- always overshoots, because the robot
 * cannot stop instantly. Scaling the power by the error means the robot is already crawling
 * by the time it gets there.
 *
 * THE TWO NUMBERS YOU WILL ACTUALLY TUNE are TURN_GAIN and MIN_TURN_POWER.
 *
 *   TURN_GAIN too low and the robot creeps towards the target forever. Too high and it
 *   oscillates: overshoot, correct, overshoot the other way, and never settle.
 *
 *   MIN_TURN_POWER exists because of STICTION. Below some power a drivetrain does not move at
 *   all, it just hums -- so with a small error, proportional control alone asks for a power
 *   too small to break friction, and the robot stops short with the error still there,
 *   apparently ignoring you. The floor is what gets it over that line.
 *
 * Tune gain first, with the floor at zero, until it settles without oscillating. Then raise
 * the floor until the last degree of error actually closes.
 *
 * TO RUN IT
 *   ./run.sh examples/vision-teamcode --robot examples/vision-samples.robot.json
 *
 * The DECODE field has AprilTags on it; press the FIELD chip to pick it. Start the robot
 * somewhere a tag is within a quarter turn and watch the telemetry.
 */
@Autonomous(name = "Vision: Aim at a Tag", group = "Vision")
public class VisionTagAuto extends LinearOpMode {

    private DcMotor frontLeft;
    private DcMotor backLeft;
    private DcMotor backRight;
    private DcMotor frontRight;

    private AprilTagProcessor aprilTag;
    private VisionPortal visionPortal;

    /* Power per degree of bearing error. See the header before changing it. */
    private static final double TURN_GAIN = 0.015;

    /* The least power that actually turns the robot rather than humming at it. */
    private static final double MIN_TURN_POWER = 0.12;

    /* Never turn faster than this while aiming, however big the error. */
    private static final double MAX_TURN_POWER = 0.35;

    /* Close enough to call it aimed. A camera's bearing is not accurate to a tenth of a
     * degree, so demanding one would be asking the robot to chase noise for ever. */
    private static final double AIM_TOLERANCE_DEG = 1.5;

    /* Give up rather than spin all match: a tag can be genuinely absent. */
    private static final double SEARCH_TIMEOUT_S = 5.0;

    @Override
    public void runOpMode() {
        frontLeft  = hardwareMap.get(DcMotor.class, "front_left_drive");
        backLeft   = hardwareMap.get(DcMotor.class, "back_left_drive");
        backRight  = hardwareMap.get(DcMotor.class, "back_right_drive");
        frontRight = hardwareMap.get(DcMotor.class, "front_right_drive");

        frontLeft.setDirection(DcMotor.Direction.REVERSE);
        backLeft.setDirection(DcMotor.Direction.REVERSE);
        backRight.setDirection(DcMotor.Direction.FORWARD);
        frontRight.setDirection(DcMotor.Direction.FORWARD);

        for (DcMotor m : new DcMotor[] { frontLeft, backLeft, backRight, frontRight }) {
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        aprilTag = new AprilTagProcessor.Builder().build();
        visionPortal = new VisionPortal.Builder()
                .setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"))
                .addProcessor(aprilTag)
                .build();

        telemetry.addData("Status", "Camera ready. Press Start.");
        telemetry.update();

        waitForStart();

        if (opModeIsActive()) {
            boolean aimed = aimAtNearestTag();

            // Leaving the tile is worth points on its own in most games, and it is worth
            // doing whether or not the aim succeeded -- a robot that found no tag and then
            // also sat still has scored nothing at all.
            driveForwardFor(1.2, 0.35);
            stopDriving();

            telemetry.addData("Result", aimed ? "aimed, then left the tile" : "no tag found, left the tile anyway");
            telemetry.update();
        }

        if (visionPortal != null) visionPortal.close();
    }

    /* Turn until the nearest tag is straight ahead. True if it got there. */
    private boolean aimAtNearestTag() {
        double deadline = getRuntime() + SEARCH_TIMEOUT_S;

        while (opModeIsActive() && getRuntime() < deadline) {
            AprilTagDetection tag = nearestTag();

            if (tag == null) {
                // NOTHING IN FRAME. Turning slowly one way is a search: it sweeps the camera
                // across the field until something appears. Standing still and waiting would
                // be a robot that cannot recover from starting a few degrees off.
                setTurnPower(MIN_TURN_POWER);
                telemetry.addData("Aiming", "no tag in frame -- sweeping");
                telemetry.update();
                continue;
            }

            double error = tag.ftcPose.bearing;
            if (Math.abs(error) <= AIM_TOLERANCE_DEG) {
                stopDriving();
                telemetry.addData("Aiming", "on target: ID %d at %.1f in", tag.id, tag.ftcPose.range);
                telemetry.update();
                return true;
            }

            // The three steps from the header, in one expression: proportional to the error,
            // floored so it moves at all, capped so it stays controllable.
            double power = clamp(TURN_GAIN * error, MIN_TURN_POWER, MAX_TURN_POWER);
            setTurnPower(power);

            telemetry.addData("Aiming", "ID %d  bearing %.1f deg  power %.2f", tag.id, error, power);
            telemetry.update();
        }

        stopDriving();
        return false;
    }

    /* The closest tag the library knows about, or null. */
    private AprilTagDetection nearestTag() {
        List<AprilTagDetection> detections = aprilTag.getDetections();
        AprilTagDetection best = null;
        for (AprilTagDetection tag : detections) {
            // Skip tags with no metadata: without the real-world size there is no bearing to
            // steer on. See the note in VisionTagTeleOp.
            if (tag.metadata == null) continue;
            if (best == null || tag.ftcPose.range < best.ftcPose.range) best = tag;
        }
        return best;
    }

    private void driveForwardFor(double seconds, double power) {
        double until = getRuntime() + seconds;
        frontLeft.setPower(power);
        backLeft.setPower(power);
        backRight.setPower(power);
        frontRight.setPower(power);
        while (opModeIsActive() && getRuntime() < until) {
            telemetry.addData("Leaving", "%.1f s left", until - getRuntime());
            telemetry.update();
        }
    }

    /* Positive turns one way, negative the other: the two sides get opposite powers. */
    private void setTurnPower(double power) {
        frontLeft.setPower(-power);
        backLeft.setPower(-power);
        backRight.setPower(power);
        frontRight.setPower(power);
    }

    private void stopDriving() {
        frontLeft.setPower(0);
        backLeft.setPower(0);
        backRight.setPower(0);
        frontRight.setPower(0);
    }

    /* Keep the sign, force the magnitude between a floor and a ceiling. */
    private static double clamp(double value, double min, double max) {
        double magnitude = Math.min(Math.max(Math.abs(value), min), max);
        return Math.signum(value) * magnitude;
    }
}
