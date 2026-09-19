package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.IMU;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/*
 * A TeleOp for examples/tank-starter.robot.json: two drive motors and an IMU.
 *
 * START HERE IF YOU ARE NEW. This is the smallest OpMode in this repository that is still a
 * real one. It reads the gamepad, drives the robot, and puts something useful on the Driver
 * Station -- and that is the whole job of a TeleOp. Everything else teams write is this with
 * more mechanisms bolted on.
 *
 * ARCADE DRIVE, AND WHY NOT TANK DRIVE. A two-motor robot can be driven two ways. TANK drive
 * puts one stick on each side: push both forward to go forward, push one to turn. ARCADE drive
 * puts forward/back on one stick axis and turning on another, so one thumb drives. Arcade is
 * what nearly every team ends up using, because it leaves the other stick free and because
 * driving a straight line does not depend on two thumbs agreeing.
 *
 * The conversion is two lines of arithmetic, in arcadeDrive() at the bottom.
 *
 * THE THING THAT TRIPS EVERYONE UP: pushing a gamepad stick FORWARD gives a NEGATIVE y. That
 * is not a bug in your robot or in this simulator; it is how every gamepad in the world
 * reports. So the call below negates it, once, and after that "forward" means forward.
 *
 * TO RUN IT
 *   ./run.sh examples/tank-teamcode --robot examples/tank-starter.robot.json
 *
 * Then press the ROBOT chip if you want to look at the robot, pick this OpMode, INIT, Start.
 * A connected controller is used automatically; otherwise WASD drives and Q/E turns.
 */
@TeleOp(name = "Tank: Arcade Drive", group = "Tank")
public class TankTeleOp extends OpMode {

    // The names here MUST match examples/tank-starter.robot.json, character for character.
    // Spell one differently and the simulator invents the device on demand -- the OpMode will
    // run, and that motor will silently do nothing.
    private DcMotor leftDrive;
    private DcMotor rightDrive;
    private IMU imu;

    // SLOW MODE. Full speed is right for crossing the field and wrong for lining up on a
    // scoring position, so the left bumper scales everything down. 0.35 is a starting point:
    // fast enough to still be useful, slow enough that a twitch does not cost you the shot.
    private static final double SLOW_FACTOR = 0.35;

    @Override
    public void init() {
        leftDrive  = hardwareMap.get(DcMotor.class, "left_drive");
        rightDrive = hardwareMap.get(DcMotor.class, "right_drive");
        imu        = hardwareMap.get(IMU.class, "imu");

        // One side has to be reversed, because the two motors face opposite ways: bolted to
        // the left and right of the same chassis, "forwards" for one is backwards for the
        // other. If the robot spins when you push the stick forward, this is the line to
        // change -- and change ONLY this one, not both.
        leftDrive.setDirection(DcMotor.Direction.REVERSE);
        rightDrive.setDirection(DcMotor.Direction.FORWARD);

        // BRAKE makes the robot stop when you let go of the stick instead of rolling on.
        // FLOAT (the default) coasts. Brake is almost always what you want for driving.
        leftDrive.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        rightDrive.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        telemetry.addData("Status", "Initialised. Press Start.");
    }

    @Override
    public void loop() {
        // Negated, once, for the reason in the header.
        double forward = -gamepad1.left_stick_y;
        double turn    =  gamepad1.right_stick_x;

        double scale = gamepad1.left_bumper ? SLOW_FACTOR : 1.0;
        arcadeDrive(forward * scale, turn * scale);

        // WHAT TO PUT ON THE DRIVER STATION. Not everything -- a wall of numbers is a wall.
        // The useful ones are what you asked for, what the robot did with it, and the one
        // sensor reading you would want if the robot did something surprising.
        YawPitchRollAngles angles = imu.getRobotYawPitchRollAngles();
        telemetry.addData("Drive", "forward %.2f  turn %.2f%s",
                forward, turn, gamepad1.left_bumper ? "  (SLOW)" : "");
        telemetry.addData("Motors", "left %.2f  right %.2f",
                leftDrive.getPower(), rightDrive.getPower());
        telemetry.addData("Heading", "%.1f deg", angles.getYaw(AngleUnit.DEGREES));
    }

    /*
     * Turn "how fast forward" and "how hard to turn" into a power for each side.
     *
     * Adding the turn to one side and subtracting it from the other is the whole idea: equal
     * powers drive straight, and a difference between them turns.
     *
     * THE NORMALISE STEP MATTERS. Full forward plus full turn is 1 + 1 = 2, and a motor's
     * power only goes to 1. If you just clipped it, the robot would drive straight at full
     * throttle and refuse to turn at all -- both sides pinned at 1. Dividing both by the
     * largest keeps their RATIO, so the robot still turns as hard as you asked, just as fast
     * as it can.
     */
    private void arcadeDrive(double forward, double turn) {
        double left  = forward + turn;
        double right = forward - turn;

        double max = Math.max(Math.abs(left), Math.abs(right));
        if (max > 1.0) {
            left  /= max;
            right /= max;
        }

        leftDrive.setPower(left);
        rightDrive.setPower(right);
    }
}
