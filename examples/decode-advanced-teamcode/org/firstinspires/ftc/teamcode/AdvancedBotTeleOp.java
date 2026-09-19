package org.firstinspires.ftc.teamcode;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.Servo;

import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

/*
 * A TeleOp for examples/decode-advanced-bot.robot.json, which touches every one of its eleven
 * devices.
 *
 * THAT IS THE POINT OF THIS FILE, twice over. It is a starting point for a robot with a lot
 * bolted to it -- but it also doubles as a WIRING CHECK. Run it, work every control, and
 * anything that does not move is a device whose name in the robot configuration does not match
 * the name in this code. The simulator will report "0 devices invented" when they all match,
 * and invent one silently when they do not.
 *
 * TWO DRIVERS, WHICH IS WHAT NEARLY EVERY TEAM ENDS UP DOING.
 *
 *   GAMEPAD 1 drives.        left stick translates, right stick turns, left bumper is slow mode
 *   GAMEPAD 2 works the bot. triggers for the intake, right bumper to shoot, dpad for the hood,
 *                            A to toggle the kickstand
 *
 * The reason to split them is not that one person cannot do both. It is that AIMING and
 * DRIVING want the same thumb at the same moment, and the driver who is lining up a shot
 * should not have to stop steering to change the hood angle.
 *
 * THE FLYWHEEL IS ALWAYS ON once the match starts, and that is deliberate rather than lazy.
 * A heavy wheel takes over a second to spin up from nothing, which is a second you do not have
 * when the shot is there. Leaving it spinning costs battery and saves the shot. Hold the right
 * bumper and the indexer feeds it; let go and the wheel keeps turning, ready.
 *
 * TO RUN IT
 *   ./run.sh examples/decode-advanced-teamcode --robot examples/decode-advanced-bot.robot.json --field examples/decode.field.json
 */
@TeleOp(name = "Advanced: Full TeleOp", group = "Advanced")
public class AdvancedBotTeleOp extends OpMode {

    private DcMotor leftFront, leftRear, rightRear, rightFront;
    private DcMotorEx shooter;
    private DcMotor intake, indexer, kickstand;
    private Servo hood;
    private GoBildaPinpointDriver pinpoint;
    private Limelight3A limelight;

    /*
     * FLYWHEEL SPEED, in encoder ticks per second, which is what setVelocity() wants.
     *
     * The shooter is a bare 6000 RPM motor whose encoder reports 28 ticks per revolution, so
     * 1000 ticks/s is about 2140 RPM. On a real robot this number comes from a distance table
     * -- further away wants more speed -- and this file uses one speed because a fixed shot
     * is the right thing to get working first.
     */
    private static final double SHOOTER_TICKS_PER_SEC = 1000.0;

    /*
     * HOW FAR BELOW TARGET COUNTS AS "a ball just left".
     *
     * Feeding a ball into a spinning wheel steals energy from it, so the measured velocity
     * DIPS. Watching for that dip is how a robot with no sensor in the barrel counts its
     * shots -- and it is more reliable than a timer, because it measures the thing that
     * actually happened. 80 ticks/s was measured on a real robot.
     */
    private static final double SHOT_DIP_TICKS = 80.0;

    /*
     * THE HOOD, and why 0.0 is not "down".
     *
     * A servo position is a number from 0 to 1 and means nothing by itself. On this robot
     * 0.0 is 57.7 degrees and 1.0 is 46.3 -- so a HIGHER position is a FLATTER shot, which is
     * the opposite of what most people assume. examples/decode-advanced-bot.robot.json
     * explains where those two angles came from.
     */
    private static final double HOOD_STEP = 0.02;
    private double hoodPosition = 0.5;

    private boolean kickstandDown = false;
    private boolean aWasPressed = false;

    private double lastShooterVelocity = 0;
    private int shotsSeen = 0;

    @Override
    public void init() {
        leftFront  = hardwareMap.get(DcMotor.class, "leftFront");
        leftRear   = hardwareMap.get(DcMotor.class, "leftRear");
        rightRear  = hardwareMap.get(DcMotor.class, "rightRear");
        rightFront = hardwareMap.get(DcMotor.class, "rightFront");

        shooter   = hardwareMap.get(DcMotorEx.class, "shooter");
        intake    = hardwareMap.get(DcMotor.class, "intake");
        indexer   = hardwareMap.get(DcMotor.class, "indexer");
        kickstand = hardwareMap.get(DcMotor.class, "kickstand");
        hood      = hardwareMap.get(Servo.class, "hood");

        pinpoint  = hardwareMap.get(GoBildaPinpointDriver.class, "pinpoint");
        limelight = hardwareMap.get(Limelight3A.class, "limelight");

        leftFront.setDirection(DcMotor.Direction.REVERSE);
        leftRear.setDirection(DcMotor.Direction.REVERSE);
        rightRear.setDirection(DcMotor.Direction.FORWARD);
        rightFront.setDirection(DcMotor.Direction.FORWARD);

        for (DcMotor m : new DcMotor[] { leftFront, leftRear, rightRear, rightFront }) {
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        // RUN_USING_ENCODER is what makes setVelocity() mean anything: the motor controller
        // reads the encoder and varies the power to HOLD the speed you asked for. Without it
        // you are setting power directly, and the speed sags the moment a ball goes through.
        shooter.setMode(DcMotor.RunMode.RUN_USING_ENCODER);

        hood.setPosition(hoodPosition);

        // The camera has to be told to start, and which pipeline to use. Pipeline 0 on this
        // robot is the AprilTag one.
        limelight.pipelineSwitch(0);
        limelight.start();

        telemetry.addData("Status", "Initialised. GP1 drives, GP2 shoots.");
    }

    @Override
    public void start() {
        // Spin the flywheel up as the match begins, for the reason in the header.
        shooter.setVelocity(SHOOTER_TICKS_PER_SEC);
    }

    @Override
    public void loop() {
        double scale = gamepad1.left_bumper ? 0.35 : 1.0;
        mecanumDrive(-gamepad1.left_stick_y * scale,
                      gamepad1.left_stick_x * scale,
                      gamepad1.right_stick_x * scale);

        // INTAKE on the triggers, so it is proportional: a gentle roll to nudge a ball, full
        // power to sweep one up. Right pulls in, left spits out -- worth having, because the
        // thing an intake does most often at competition is jam.
        intake.setPower(gamepad2.right_trigger - gamepad2.left_trigger);

        shoot();
        moveHood();
        toggleKickstand();
        report();
    }

    @Override
    public void stop() {
        shooter.setVelocity(0);
        indexer.setPower(0);
        intake.setPower(0);
    }

    /*
     * Hold the right bumper to feed balls into the flywheel, and count what leaves.
     */
    private void shoot() {
        double velocity = shooter.getVelocity();

        // The indexer runs only when the wheel is up to speed. Feeding a slow wheel is how a
        // ball dribbles out and a jam starts -- the ball goes in, the wheel does not have the
        // energy to throw it, and now there is a ball wedged against a stalled wheel.
        boolean upToSpeed = velocity >= SHOOTER_TICKS_PER_SEC - SHOT_DIP_TICKS;
        indexer.setPower(gamepad2.right_bumper && upToSpeed ? 1.0 : 0.0);

        // COUNT THE DIPS. A drop of more than SHOT_DIP_TICKS between one loop and the next is
        // a ball taking energy out of the wheel. Comparing against the PREVIOUS reading rather
        // than against the target is what makes this count separate shots instead of counting
        // every loop of a long spin-up.
        if (lastShooterVelocity - velocity > SHOT_DIP_TICKS) shotsSeen++;
        lastShooterVelocity = velocity;
    }

    /* Dpad up and down nudge the hood. Up is a STEEPER shot -- see HOOD_STEP above. */
    private void moveHood() {
        if (gamepad2.dpad_up)   hoodPosition -= HOOD_STEP;
        if (gamepad2.dpad_down) hoodPosition += HOOD_STEP;

        // Clamp before writing. A servo asked for 1.4 does not go further; it goes to 1.0 and
        // the variable keeps climbing, so the first few presses back the other way do nothing
        // at all and the driver thinks the hood has stuck.
        hoodPosition = Math.max(0.0, Math.min(1.0, hoodPosition));
        hood.setPosition(hoodPosition);
    }

    /*
     * A on gamepad 2 toggles the kickstand.
     *
     * THE RISING-EDGE CHECK is the point of this method. gamepad2.a is true for EVERY loop the
     * button is held, and the loop runs about fifty times a second -- so toggling on the raw
     * value flips the state fifty times a second and lands wherever it happens to stop.
     * Remembering what the button was last time, and acting only on false-to-true, turns a
     * held button into one press.
     */
    private void toggleKickstand() {
        boolean pressed = gamepad2.a;
        if (pressed && !aWasPressed) kickstandDown = !kickstandDown;
        aWasPressed = pressed;

        kickstand.setPower(kickstandDown ? 0.5 : 0.0);
    }

    private void report() {
        telemetry.addData("Shooter", "%.0f / %.0f ticks/s%s",
                shooter.getVelocity(), SHOOTER_TICKS_PER_SEC,
                gamepad2.right_bumper ? "   FEEDING" : "");
        telemetry.addData("Shots seen", shotsSeen);
        telemetry.addData("Hood", "%.2f  (%.1f deg)", hoodPosition, hoodAngleDeg());
        telemetry.addData("Intake", "%.2f", intake.getPower());
        telemetry.addData("Kickstand", kickstandDown ? "DOWN" : "up");

        // WHERE THE ROBOT THINKS IT IS. Odometry pods measure the floor going past rather
        // than the wheels turning, so this survives wheel slip in a way drive encoders do not.
        pinpoint.update();
        telemetry.addData("Odometry", "x %.1f in  y %.1f in  heading %.1f deg",
                pinpoint.getPosX(DistanceUnit.INCH),
                pinpoint.getPosY(DistanceUnit.INCH),
                pinpoint.getHeading(AngleUnit.DEGREES));

        // WHAT THE CAMERA SEES. Always check isValid() first: a result with no target still
        // arrives, with tx and ty at zero, and code that skips the check will happily aim at
        // a target that is not there.
        LLResult result = limelight.getLatestResult();
        if (result != null && result.isValid()) {
            telemetry.addData("Limelight", "tx %.1f  ty %.1f", result.getTx(), result.getTy());
        } else {
            telemetry.addData("Limelight", "no target");
        }
    }

    /* The hood's real angle, for telemetry a human can act on. */
    private double hoodAngleDeg() {
        return 57.7 + hoodPosition * (46.3 - 57.7);
    }

    private void mecanumDrive(double forward, double strafe, double rotate) {
        double lf = forward + strafe + rotate;
        double rf = forward - strafe - rotate;
        double lr = forward - strafe + rotate;
        double rr = forward + strafe - rotate;

        double max = Math.max(Math.max(Math.abs(lf), Math.abs(rf)),
                              Math.max(Math.abs(lr), Math.abs(rr)));
        if (max > 1.0) { lf /= max; rf /= max; lr /= max; rr /= max; }

        leftFront.setPower(lf);
        rightFront.setPower(rf);
        leftRear.setPower(lr);
        rightRear.setPower(rr);
    }
}
