abstract class Cart(var p8 : Pico8) {
    abstract fun map_data(): String
    abstract fun flag_data(): String

    abstract fun init()

    abstract fun update()
    abstract fun draw()
}