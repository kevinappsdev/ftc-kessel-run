package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DistanceSensor;
import com.qualcomm.robotcore.hardware.IMU;
import com.qualcomm.robotcore.hardware.NormalizedColorSensor;
import com.qualcomm.robotcore.hardware.NormalizedRGBA;
import com.qualcomm.robotcore.hardware.TouchSensor;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/*
 * Every sensor on examples/first-samples.robot.json, read at once, while you drive.
 *
 * WHY THIS EXISTS ALONGSIDE FIRST'S OWN SAMPLES. FIRST ship one sample per sensor, and each
 * one is the right thing to read when you are learning that sensor: SensorColor, SensorREV2m
 * Distance, SensorDigitalTouch, SensorIMUOrthogonal. They are in the built-in list already --
 * close your project with the PROJECT chip and they are there.
 *
 * What none of them can show you is all four AT ONCE, on a robot that is moving. That is the
 * view you actually want, twice:
 *
 *   WHEN YOU WIRE A ROBOT UP. Drive it around, put your hand in front of the distance sensor,
 *   press the touch sensor, and watch the numbers move. A sensor that reads a sensible
 *   constant when nothing is happening looks identical to a sensor plugged into the wrong
 *   port -- until you make something happen and only three of the four react.
 *
 *   WHEN YOU ARE DECIDING A THRESHOLD. "Close enough to the wall" and "over the red tape" are
 *   numbers you have to pick, and the only honest way to pick one is to drive to the place
 *   that matters and read what the sensor actually says there.
 *
 * THE NAMES ARE FIRST'S, not this project's -- sensor_distance, sensor_color, digitalTouch,
 * imu -- because first-samples.robot.json exists to carry exactly what FIRST's samples ask
 * for, by name, so their code runs unmodified. Keep them if you want their samples to keep
 * working.
 *
 * TO RUN IT
 *   ./run.sh examples/first-samples-teamcode --robot examples/first-samples.robot.json
 */
@TeleOp(name = "Sensors: Read Everything", group = "Samples")
public class SensorReadoutTeleOp extends OpMode {

    private DcMotor leftDrive;
    private DcMotor rightDrive;
    private DcMotor leftBackDrive;
    private DcMotor rightBackDrive;

    private IMU imu;
    private DistanceSensor distance;
    private NormalizedColorSensor colour;
    private TouchSensor touch;

    @Override
    public void init() {
        leftDrive      = hardwareMap.get(DcMotor.class, "left_drive");
        rightDrive     = hardwareMap.get(DcMotor.class, "right_drive");
        leftBackDrive  = hardwareMap.get(DcMotor.class, "left_back_drive");
        rightBackDrive = hardwareMap.get(DcMotor.class, "right_back_drive");

        // EACH SENSOR IS ASKED FOR BY INTERFACE, not by the make of the part. A
        // Rev2mDistanceSensor is a DistanceSensor, and so is the distance half of a colour
        // sensor; asking for the interface means swapping the part later changes the robot
        // configuration and not this file.
        imu      = hardwareMap.get(IMU.class, "imu");
        distance = hardwareMap.get(DistanceSensor.class, "sensor_distance");
        colour   = hardwareMap.get(NormalizedColorSensor.class, "sensor_color");
        touch    = hardwareMap.get(TouchSensor.class, "digitalTouch");

        leftDrive.setDirection(DcMotor.Direction.REVERSE);
        leftBackDrive.setDirection(DcMotor.Direction.REVERSE);
        rightDrive.setDirection(DcMotor.Direction.FORWARD);
        rightBackDrive.setDirection(DcMotor.Direction.FORWARD);

        telemetry.addData("Status", "Initialised. Press Start and drive.");
    }

    @Override
    public void loop() {
        mecanumDrive(-gamepad1.left_stick_y, gamepad1.left_stick_x, gamepad1.right_stick_x);

        telemetry.addLine("--- what the sensors say ---");

        // DISTANCE. Out of range is not an error and not zero: the sensor returns a very large
        // number, or NaN, when there is nothing in front of it. Code that treats "no reading"
        // as "zero inches" thinks the wall is touching the robot, which is the worst possible
        // wrong answer. So check for it rather than passing it on.
        double inches = distance.getDistance(DistanceUnit.INCH);
        telemetry.addData("Distance", Double.isNaN(inches) || inches > 200
                ? "nothing in range" : String.format("%.1f in", inches));

        // COLOUR, NORMALISED. The raw counts depend on how bright the room is, so they change
        // when somebody opens a door. Normalised values are scaled against the overall light
        // level, which is what makes "is this red or blue" survive a different venue.
        NormalizedRGBA rgba = colour.getNormalizedColors();
        telemetry.addData("Colour", "r %.3f  g %.3f  b %.3f  (alpha %.3f)",
                rgba.red, rgba.green, rgba.blue, rgba.alpha);

        // TOUCH. isPressed() is the whole API, and it is the sensor to reach for whenever a
        // mechanism has an end: a lift that must not drive past the top, an arm that needs to
        // find zero. Counting encoder ticks tells you where you THINK it is; a touch sensor
        // tells you where it IS.
        telemetry.addData("Touch", touch.isPressed() ? "PRESSED" : "released");

        telemetry.addData("Heading", "%.1f deg",
                imu.getRobotYawPitchRollAngles().getYaw(AngleUnit.DEGREES));
    }

    private void mecanumDrive(double forward, double strafe, double rotate) {
        double lf = forward + strafe + rotate;
        double rf = forward - strafe - rotate;
        double lb = forward - strafe + rotate;
        double rb = forward + strafe - rotate;

        double max = Math.max(Math.max(Math.abs(lf), Math.abs(rf)),
                              Math.max(Math.abs(lb), Math.abs(rb)));
        if (max > 1.0) { lf /= max; rf /= max; lb /= max; rb /= max; }

        leftDrive.setPower(lf);
        rightDrive.setPower(rf);
        leftBackDrive.setPower(lb);
        rightBackDrive.setPower(rb);
    }
}
