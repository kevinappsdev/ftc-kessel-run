package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;

import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.vision.VisionPortal;
import org.firstinspires.ftc.vision.apriltag.AprilTagDetection;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;

import java.util.List;

/*
 * Drive, and see what the camera sees, on examples/vision-samples.robot.json.
 *
 * WHAT AN APRILTAG GIVES YOU, and why it is worth the trouble. A tag is a printed square whose
 * pattern is a number. Because the camera knows how big the square really is, it can work
 * backwards from how big and how skewed the square LOOKS to where the camera must be standing
 * relative to it. So one tag in frame answers "how far away am I and which way am I facing" --
 * the question dead reckoning cannot answer after the first collision.
 *
 * THE THREE NUMBERS TO LEARN FIRST, all in ftcPose:
 *
 *   range     how far the tag is, in inches
 *   bearing   how far LEFT or RIGHT of straight ahead it is, in degrees. Positive is left.
 *   yaw       how far round the tag itself is turned from facing you
 *
 * Bearing is the one you steer on, and VisionTagAuto in this folder does exactly that. Range
 * is the one you shoot on. Yaw is the one you need if you want to end up square to something
 * rather than merely pointing at it.
 *
 * WHAT TO WATCH FOR WHILE YOU DRIVE. Detection is not free and not constant: turn away and the
 * list empties, drive far enough and it empties, and at the edge of range it will flicker in
 * and out between loops. Code that assumes there is always a detection will work beautifully
 * on the bench and throw on the field. Every read below goes through a "did we see anything"
 * check for that reason.
 *
 * TO RUN IT
 *   ./run.sh examples/vision-teamcode --robot examples/vision-samples.robot.json
 */
@TeleOp(name = "Vision: Drive and See Tags", group = "Vision")
public class VisionTagTeleOp extends OpMode {

    private DcMotor frontLeft;
    private DcMotor backLeft;
    private DcMotor backRight;
    private DcMotor frontRight;

    private AprilTagProcessor aprilTag;
    private VisionPortal visionPortal;

    @Override
    public void init() {
        // FIRST'S OWN NAMES, so their vision samples run against this robot unmodified.
        frontLeft  = hardwareMap.get(DcMotor.class, "front_left_drive");
        backLeft   = hardwareMap.get(DcMotor.class, "back_left_drive");
        backRight  = hardwareMap.get(DcMotor.class, "back_right_drive");
        frontRight = hardwareMap.get(DcMotor.class, "front_right_drive");

        frontLeft.setDirection(DcMotor.Direction.REVERSE);
        backLeft.setDirection(DcMotor.Direction.REVERSE);
        backRight.setDirection(DcMotor.Direction.FORWARD);
        frontRight.setDirection(DcMotor.Direction.FORWARD);

        // The processor finds tags in a frame. The portal runs the camera and feeds frames to
        // it. Two objects because one camera can drive several processors at once -- tags and
        // a colour pipeline together, say.
        aprilTag = new AprilTagProcessor.Builder().build();
        visionPortal = new VisionPortal.Builder()
                .setCamera(hardwareMap.get(WebcamName.class, "Webcam 1"))
                .addProcessor(aprilTag)
                .build();

        telemetry.addData("Status", "Camera starting. Press Start.");
    }

    @Override
    public void loop() {
        mecanumDrive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);

        List<AprilTagDetection> detections = aprilTag.getDetections();
        telemetry.addData("Tags in view", detections.size());

        for (AprilTagDetection tag : detections) {
            // metadata is null for a tag whose id is not in the season's library -- a tag you
            // printed yourself, or one from last year. It is still DETECTED, and its centre in
            // the image is still valid; what is missing is the real-world size, and without
            // that there is no range or bearing to report.
            if (tag.metadata == null) {
                telemetry.addLine(String.format("ID %d  (not in the tag library)", tag.id));
                continue;
            }
            telemetry.addLine(String.format("ID %d %s", tag.id, tag.metadata.name));
            telemetry.addLine(String.format("   range %.1f in   bearing %.1f deg   yaw %.1f deg",
                    tag.ftcPose.range, tag.ftcPose.bearing, tag.ftcPose.yaw));
        }

        if (detections.isEmpty()) {
            telemetry.addLine("Nothing in frame. Turn until a tag comes into view.");
        }
    }

    @Override
    public void stop() {
        // Let the camera go. Without this it can stay held after the OpMode ends, and the next
        // OpMode that wants it finds it busy.
        if (visionPortal != null) visionPortal.close();
    }

    private void mecanumDrive(double forward, double strafe, double rotate) {
        double lf = forward + strafe + rotate;
        double rf = forward - strafe - rotate;
        double lb = forward - strafe + rotate;
        double rb = forward + strafe - rotate;

        double max = Math.max(Math.max(Math.abs(lf), Math.abs(rf)),
                              Math.max(Math.abs(lb), Math.abs(rb)));
        if (max > 1.0) { lf /= max; rf /= max; lb /= max; rb /= max; }

        frontLeft.setPower(lf);
        frontRight.setPower(rf);
        backLeft.setPower(lb);
        backRight.setPower(rb);
    }
}
