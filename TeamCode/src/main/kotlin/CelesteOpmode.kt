import Pico8.load_game
import Pico8.set_btn_state
import Celeste.load_room
import Pico8.set_inputs
import Pico8.step
import Celeste.getString
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode
import kotlin.Throws

@TeleOp
class CelesteOpmode : LinearOpMode() {
    @Throws(InterruptedException::class)
    override fun runOpMode() {
        val p8 = Pico8()
        val celeste = Celeste(p8)
        p8.load_game(celeste)
        p8.set_btn_state(0)
        celeste.load_room(0 % 8, 0)
        telemetry.msTransmissionInterval = 15
        while (!isStarted) {
            val start = System.currentTimeMillis()
            p8.set_btn_state(0)
            p8.set_inputs(gamepad1.dpad_left, gamepad1.dpad_right, gamepad1.dpad_up, gamepad1.dpad_down, gamepad1.a || gamepad1.y, gamepad1.x || gamepad1.b)
            p8.step()
            val print = celeste.getString().replace("\n", "<br>")
            telemetry.addLine(print)
            telemetry.update()
            while (System.currentTimeMillis() - start < 33);
        }
    }
}