import kotlin.math.*

class Celeste(p8: Pico8) : Cart(p8) {

    data class Vector(var x: Double, var y: Double){
        constructor(x: Int, y: Int): this(x.toDouble(), y.toDouble())
    }
    data class Rect(var x : Int, var y : Int, var w : Int, var h : Int)

    private var has_key = false
    var has_dashed = false
    var room = Vector(0, 0)
    var objects = ArrayList<base_obj>()
    var freeze = 0
    var delay_restart = 0
    var next_rm = true

    var max_djump = 1

    var k_left = 0
    var k_right = 1
    var k_up = 2
    var k_down = 3
    var k_jump = 4
    var k_dash = 5

    var frames = 0
    var tiles = arrayOf(1, 8, 11, 12, 18, 20, 22, 23, 26, 28, 64, 86, 96, 118)

    /**
    self.tiles = {
    1: self.player_spawn,
    8: self.key,
    11: self.platform,
    12: self.platform,
    18: self.spring,
    20: self.chest,
    22: self.balloon,
    23: self.fall_floor,
    26: self.fruit,
    28: self.fly_fruit,
    64: self.fake_wall,
    #86: self.message,
    #96: self.big_chest,
    #118: self.flag
    }
     */

    override fun init() {
        frames = 0
        load_room(0, 0)
    }

    // main update loop

    override fun update() {
        frames = (frames + 1) % 30

        if(freeze > 0){
            freeze -= 1
            return
        }

        if(delay_restart > 0) {
            delay_restart -= 1
            if(delay_restart == 0){
                load_room(room.x, room.y)
            }
        }

        for(o in objects) {
            o.move(o.spd.x.toInt(), o.spd.y.toInt())
            o.update()
        }

        // [change] decouple next room from object loop
        if(next_rm) {
            next_rm = false
            var lvl_id = level_index()
            var n_objs = objects.size
            load_room(lvl_id % 8, floor(lvl_id / 8.0).toInt())
            //simulate loading jank
            if(lvl_id > 0) {
                for(o in objects.drop(n_objs - 1)){
                    o.move(o.spd.x.toInt(), o.spd.y.toInt())
                    o.update()
                }
            }
        }

        //[change] remove destroyed objects after update calls
        //while objects.count(None) > 0: self.objects.remove(None)
    }

    // main draw loop (not actually for drawing)

    override fun draw(){
        if(freeze > 0)
            return

        for(o in objects) {
            if(o is player) {
                o.draw()
            }
        }
    }

    //room stuff

    fun level_index(): Int {
        return (room.x.toInt() + room.y.toInt() * 8).toInt()
    }

    fun restart_room() {
        delay_restart = 15
    }

    fun next_room(loop: Boolean = false) {
        //[change] decouple next room from object loop
        room = Vector((level_index() + 1) % 8, floor((level_index() + 1) / 8.0).toInt())
        next_rm = true
    }

    fun load_room(x: Number, y: Number) {
        has_dashed = false
        has_key = false
        objects = ArrayList<base_obj>()
        room.x = x.toDouble()
        room.y = y.toDouble()
        for (tx in 0 until 16) {
            for (ty in 0 until 16) {
                val tile = p8.mget(room.x.toDouble() * 16 + tx, room.y.toDouble() * 16 + ty) as Int
                if (tile in tiles) {
                    init_object(tile, tx * 8, ty * 8, tile)
                }
            }
        }
    }

    //object base class

    abstract class base_obj(var g: Celeste, var x: Double, var y: Double, tile: Int?) {
        open fun init(){}
        open fun update(){}

        var dash_time = 0
        var dash_effect_time = 0
        var djump = 0
        var collideable = true
        var solids = false
        var spr = tile

        fun Boolean.toInt() = if (this) 1 else 0

        var flip = Vector(false.toInt(), false.toInt())
        var hitbox = Rect(0, 0, 8, 8)
        var spd = Vector(0.0, 0.0)
        var rem = Vector(0.0, 0.0)

        fun is_solid(ox: Int, oy: Int) : Boolean{
            if(oy > 0 && (check(platform::class.java, ox, 0) == null) && (check(platform::class.java, ox, oy) != null)){
                return true
            }
            return g.tile_flag_at(
                x + hitbox.x + ox,
                y + hitbox.y + oy,
                hitbox.w,
                hitbox.h,
                0
            ) || (check(fall_floor::class.java, ox, oy) != null) || (check(fake_wall::class.java, ox, oy) != null)
        }


        fun is_ice(ox: Int, oy: Int): Boolean {
            return g.tile_flag_at(
                x + hitbox.x + ox,
                y + hitbox.y + oy,
                hitbox.w,
                hitbox.h,
                4
            )
        }

        fun check(obj: Class<*>?, ox: Int, oy: Int): base_obj? {
            for(other in g.objects) {
                if ((other::class.java == obj) && other != this && other.collideable &&
                        other.x + other.hitbox.x + other.hitbox.w > x + hitbox.x + ox &&
                        other.y + other.hitbox.y + other.hitbox.h > y + hitbox.y + oy &&
                        other.x + other.hitbox.x < x + hitbox.x + hitbox.w + ox &&
                        other.y + other.hitbox.y < y + hitbox.y + hitbox.h + oy) {
                    return other
                }
            }
            return null
        }

        fun move(ox: Int, oy: Int) {
            rem.x += ox
            var amt = floor(rem.x + 0.5)
            rem.x -= amt
            move_x(amt, 0)
            rem.y += oy
            amt = floor(rem.y + 0.5)
            rem.y -= amt
            move_y(amt)
        }

        fun move_x(amt: Double, start: Int) {
            if (solids) {
                var step = sign(amt)
                for(i in start until (abs(amt) + 1).toInt()) {
                    if(!(is_solid (step.toInt(), 0))) {
                        x += step
                    } else {
                        spd.x = 0.0
                        rem.x = 0.0
                        break
                    }
                }
            } else {
                x += amt
            }
        }

        fun move_y(amt: Double) {
            if (solids) {
                var step = sign(amt)
                for (i in 0 until (abs(amt) + 1).toInt()) {
                    if(!is_solid (0, step.toInt())) {
                        y += step
                    }
                    else {
                        spd.y = 0.0
                        rem.y = 0.0
                        break
                    }
                }
            }
            else {
                y += amt
            }
        }

        //def __str__(self):
        //return f'[{self.__class__.__name__}] x: {self.x}, y: {self.y}, rem: {{{self.rem.x:.4f}, {self.rem.y:.4f}}}, spd: {{{self.spd.x:.4f}, {self.spd.y:.4f}}}'
    }
    //objects

    class player_spawn(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var target = y
        var state = 0
        var delay = 0

        override fun init() {
            target = y
            y = 128.0
            spd.y = (-4).toDouble()
            state = 0
            delay = 0
        }

        override fun update() {
            //jumping up
            if (state == 0) {
                if(y < target + 16) {
                    state = 1
                    delay = 3
                }
            }
            //falling
            else if(state == 1) {
                spd.y += 0.5
                if(spd.y > 0){
                    if(delay > 0) {
                        //stall at peak
                        spd.y = 0.0
                        delay -= 1
                    } else if(y > target) {
                        //clamp at target y
                        y = target
                        spd = Vector(0.0, 0.0)
                        state = 2
                        delay = 5
                    }
                }
            }
            //landing and spawning player object
            else if(state == 2) {
                delay -= 1
                if(delay < 0){
                    g.destroy_object(this)
                    g.init_object(-1, x, y, -1)
                }
            }
        }
    }

    class player(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var p_jump = false
        var p_dash = false
        var grace = 0
        var jbuffer = 0
        var dash_target = Vector(0.0, 0.0)
        var dash_accel = Vector(0.0, 0.0)

        override fun init() {
            p_jump = false
            p_dash = false
            grace = 0
            jbuffer = 0
            djump = 1
            dash_time = 0
            dash_effect_time = 0
            dash_target = Vector(0.0, 0.0)
            dash_accel = Vector(0.0, 0.0)
            hitbox = Rect(1, 3, 6, 5)
            solids = true
        }

        override fun update() {
            //horizontal input
            val h_input = if(g.p8.btn(g.k_right)) 1 else{if (g.p8.btn(g.k_left)) -1 else 0}
            //spike collision
            if(g.spikes_at(
                    (x + hitbox.x).toInt(),
                    (y + hitbox.y).toInt(),
                hitbox.w,
                hitbox.h,
                    spd.x.toInt(),
                    spd.y.toInt()
            )) {
                g.kill_player(this)
            }

            //bottom death
            if (y > 128) {
                g.kill_player(this)
            }

            //on ground check
            var on_ground = is_solid(0, 1)

            //jump and dash inmput
            var jump = g.p8.btn(g.k_jump) && !p_jump
            var dash = g.p8.btn(g.k_dash) && !p_dash
            p_jump = g.p8.btn(g.k_jump)
            p_dash = g.p8.btn(g.k_dash)

            //jump buffer
            if(jump) {
                jbuffer = 4
            }else if(jbuffer > 0) {
                jbuffer -= 1
            }

            //grace frames and dash restoration
            if(on_ground) {
                grace = 6
                djump = g.max_djump
            } else if(grace > 0) {
                grace -= 1
            }

            //dash effect timer(for dash - triggered events, e.g., berry blocks)
            dash_effect_time -= 1

            if(dash_time > 0) {
                dash_time -= 1
                spd.x = g.appr(spd.x, dash_target.x, dash_accel.x) as Double
                spd.y = g.appr(spd.y, dash_target.y, dash_accel.y) as Double
            }
            else {
                var maxrun = 1
                var accel = (if (!is_ice (0, 1)) 0.6 else {if(on_ground) 0.05 else 0.4})
                var deccel = 0.15

                //set x speed
                spd.x = if(abs(spd.x) <= 1) g.appr(spd.x, h_input * maxrun, accel) as Double else g.appr(
                    spd.x,
                    sign(spd.x) * maxrun,
                    deccel
                ) as Double

                //facing direction
                if ((spd.x).toInt() != 0) {
                    flip.x = (spd.x < 0).toInt().toDouble()
                }

                //terminal vel +wall sliding
                var maxfall = if (!(
                    h_input != 0 && is_solid(h_input, 0) and !is_ice (h_input,
                    0
                ))) 2 else 0.4

                // apply gravity
                if (!on_ground) {
                    spd.y = g.appr(spd.y, maxfall, if (abs(spd.y) > 0.15) 0.21 else 0.105) as Double
                }

                //jump
                if (jbuffer > 0){
                    if(grace > 0) {
                        jbuffer = 0
                        grace = 0
                        spd.y = (-2).toDouble()
                    } else {
                        val wall_dir = if (is_solid(-3, 0)) -1 else if(is_solid(3, 0)) 1 else 0
                        if (wall_dir != 0) {
                            jbuffer = 0
                            spd.y = (-2).toDouble()
                            spd.x = (-wall_dir * (maxrun + 1)).toDouble()
                        }
                    }
                }

                //# dash
                val d_full = 5
                val d_half = 3.5355339059

                if (djump > 0 && dash) {
                    djump -= 1
                    dash_time = 4
                    g.has_dashed = true
                    dash_effect_time = 10
                    //# vertical input
                    val v_input = if (g.p8.btn(g.k_up)) -1 else if (g.p8.btn(g.k_down)) 1 else 0
                    //calculate dash speeds
                    spd.x =
                        h_input.toDouble() * (if (h_input != 0) (if(v_input == 0) d_full else d_half) else (if(v_input != 0 ) 0 else if (flip.x.toInt() == 1) -1 else 1)).toDouble()
                    spd.y = v_input * (if(v_input != 0) (if (h_input == 0) d_full else d_half) else 0).toDouble()
                    //# effects
                    g.freeze = 2
                    //# dash target speeds and accels
                    dash_target.x = 2 * sign(spd.x)
                    dash_target.y = (if(spd.y >= 0) 2 else 1.5).toDouble() * sign(spd.y)
                    dash_accel.x = if(spd.y.toInt() == 0) 1.5 else 1.06066017177
                    dash_accel.y = if(spd.x.toInt() == 0) 1.5 else 1.06066017177
                }
            }
            //# exit level off the top
            if(y < -4)
                g.next_room()
        }

        fun draw() {
            if (x < -1 || x > 121) {
                x = g.clamp(x, -1, 121) as Double
                spd.x = 0.0
            }
        }
    }

    class balloon(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var timer = 0
        override fun init() {
            timer = 0
            //# [change] remove rng, expand hitbox to cover balloon oscillation cycle
            hitbox = Rect(-1, -1 - 2, 10, 10 + 4)
        }

        override fun update() {
            if (spr == 22) {
                val hit = check(player::class.java, 0, 0)
                if ((hit != null) && hit.djump < g.max_djump) {
                    hit.djump = g.max_djump
                    spr = 0
                    timer = 60
                }
            }else if(timer > 0) {
                timer -= 1
            }else{
                spr = 22
            }
        }
    }

    class platform(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var last = 0.0
        var dir = 0

        override fun init() {
            x -= 4
            hitbox.w = 16
            last = x
            dir = if (spr == 11) -1 else 1
        }

        override fun update() {
            spd.x = dir * 0.65
            if (x < -16) {
                x = 128.0
            } else if (x > 128) {
                x = (-16).toDouble()
            }
            if (check(player::class.java, 0, 0) == null){
                check(player::class.java, 0, -1)?.move_x(x - last, 1)
            }
            last = x
        }
    }

    class fruit(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var start = y
        var off = 0
        override fun init() {
            start = y
            off = 0
        }

        override fun update() {
            val hit = check(player::class.java, 0, 0)
            if(hit != null) {
                hit.djump = g.max_djump
                g.destroy_object(this)
            }
            off += 1
            y = start + sin((off / 40).toDouble()) * 2.5
        }
    }

    class fly_fruit(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var fly = false
        var step = 0.5
        override fun init() {
            fly = false
            step = 0.5
            solids = false
        }

        override fun update() {
            if(fly) {
                spd.y = g.appr(spd.y, -3.5, 0.25) as Double
                if (y < -16) {
                    g.destroy_object(this)
                }
            }
            else {
                if(g.has_dashed) {
                    fly = true
                }
                step += 0.05
                spd.y = sin(step) * 0.5
            }
            val hit = check(player::class.java, 0, 0)
            if (hit != null) {
                hit.djump = g.max_djump
                g.destroy_object(this)
            }
        }
    }

    class fake_wall(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        override fun update() {
            hitbox.w = 18
            hitbox.h = 18
            val hit = check(player::class.java, -1, -1)
            if (hit != null && hit.dash_effect_time > 0) {
                hit.spd.x = -sign(hit.spd.x) * 1.5
                hit.spd.y = -1.5
                hit.dash_time = -1
                g.init_object(26, x + 4, y + 4, 26)
                g.destroy_object(this)
            }
            hitbox.w = 16
            hitbox.h = 16
        }
    }

    class spring(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var hide_in = 0
        var hide_for = 0
        var delay = 0
        override fun init() {
            hide_in = 0
            hide_for = 0
        }

        override fun update() {
            if (hide_for > 0) {
                hide_for -= 1
                if (hide_for <= 0) {
                    delay = 0
                }
            }else if(spr == 18) {
                val hit = check(player::class.java, 0, 0)
                if ((hit != null) && hit.spd.y >= 0) {
                    spr = 19
                    hit.y = y - 4
                    hit.spd.x *= 0.2
                    hit.spd.y = -3.0
                    hit.djump = g.max_djump
                    delay = 10
                    val below = check(fall_floor::class.java, 0, 1)
                    if (below != null)
                        g.break_fall_floor(below as fall_floor)
                }
            }else if (delay > 0) {
                delay -= 1
                if (delay <= 0) {
                    spr = 18
                }
            }
            if (hide_in > 0) {
                hide_in -= 1
                if (hide_in <= 0) {
                    hide_for = 60
                    spr = 0
                }
            }
        }
    }

    class fall_floor(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var state = 0
        var delay = 0
        override fun init() {
            state = 0
        }

        override fun update() {
            if (state == 0) {
                if((check(player::class.java, 0, -1) != null) || (check(player::class.java, -1, 0) != null) || (check(player::class.java, 1, 0) != null)) {
                    g.break_fall_floor(this)
                }
            }else if(state == 1) {
                delay -= 1
                if (delay <= 0) {
                    state = 2
                    delay = 60
                    collideable = false
                    spr = 0
                }
            } else if(state == 2) {
                delay -= 1
                if (delay <= 0 && (check (player::class.java, 0, 0) != null)) {
                    state = 0
                    collideable = true
                    spr = 23
                }
            }
        }
    }

    fun break_spring (obj: spring){
        obj.hide_in = 15
    }

    fun break_fall_floor (obj: fall_floor) {
        if(obj.state == 0) {
            obj.state = 1
            obj.delay = 15
            val hit = obj.check(spring::class.java, 0, -1)
            if (hit != null) {
                break_spring(hit as spring)
            }
        }
    }

    class key(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        override fun update() {
            if (check(player::class.java, 0, 0) != null) {
                g.destroy_object(this)
                g.has_key = true
            }
        }
    }

    class chest(g: Celeste, x: Double, y: Double, tile: Int?): base_obj(g, x, y, tile) {
        var timer = 0
        override fun init() {
            x -= 4
            timer = 20
        }

        override fun update() {
            if (g.has_key) {
                timer -= 1
                if (timer <= 0) {
                    val f = g.init_object(26, x, y - 4, 26) as fruit
                    //# [change] remove rng, expand fruit hitbox to cover all outcomes
                    f.hitbox.x -= 1
                    f.hitbox.w += 3
                    g.destroy_object(this)
                }
            }
        }
    }

    //# object handling stuff
    /**
     *     1: self.player_spawn,
    8: self.key,
    11: self.platform,
    12: self.platform,
    18: self.spring,
    20: self.chest,
    22: self.balloon,
    23: self.fall_floor,
    26: self.fruit,
    28: self.fly_fruit,
    64: self.fake_wall,
    #86: self.message,
    #96: self.big_chest,
    #118: self.flag
     */
    fun init_object(objType: Int, x: Number, y: Number, tile: Int?=null): base_obj {
        var obj : base_obj = when (objType){
            1 -> player_spawn(this, x.toDouble(), y.toDouble(), tile)
            8 -> key(this, x.toDouble(), y.toDouble(), tile)
            11 -> platform(this, x.toDouble(), y.toDouble(), tile)
            12 -> platform(this, x.toDouble(), y.toDouble(), tile)
            18 -> spring(this, x.toDouble(), y.toDouble(), tile)
            20 -> chest(this, x.toDouble(), y.toDouble(), tile)
            22 -> balloon(this, x.toDouble(), y.toDouble(), tile)
            23 -> fall_floor(this, x.toDouble(), y.toDouble(), tile)
            26 -> fruit(this, x.toDouble(), y.toDouble(), tile)
            28 -> fly_fruit(this, x.toDouble(), y.toDouble(), tile)
            64 -> fake_wall(this, x.toDouble(), y.toDouble(), tile)
            -1 -> player(this, x.toDouble(), y.toDouble(), tile)
            else -> {platform(this, x.toDouble(), y.toDouble(), tile)}
        }
        objects.add(obj)
        obj.init()
        return obj
    }

    fun destroy_object(obj: base_obj) {
        //# [change] remove from list later so update loop doesn't skip
        objects.remove(obj)
    }

    fun kill_player(obj: base_obj) {
        destroy_object(obj)
        restart_room()
    }

    //# helper functions

    fun get_player() : base_obj? {
        for(o in objects) {
            if (o is player_spawn || o is player)
                return o
        }
        return null
    }

    fun clamp(value: Number, a: Number, b: Number): Number {
        return max(a.toDouble(), min(b.toDouble(), value.toDouble()))
    }

    fun appr(value: Number, target: Number, amt: Number): Number {
        return if (value.toDouble() > target.toDouble()) max(value.toDouble() - amt.toDouble(), target.toDouble()) else min(value.toDouble()+amt.toDouble(), target.toDouble())
    }

    fun tile_flag_at(x: Number, y: Number, w: Number, h: Number, flag: Number): Boolean {
        for (i in (max(0, (x.toDouble() / 8).toInt())until (min(15, (x.toDouble() + w.toDouble() - 1).toInt() / 8)) + 1)) {
            for (j in (max(0, (y.toDouble() / 8).toInt()) until (min(15.0, (y.toDouble() + h.toDouble() - 1) / 8)).toInt() + 1)) {
                if (tile_at(i, j)?.let { p8.fget(it, flag) } == 1) {
                    return true
                }
            }
        }
        return false
    }

    fun tile_at(x: Int, y: Int): Int? {
        return p8.mget((room.x.toInt() * 16 + x), (room.y.toInt() * 16 + y))
    }

    fun spikes_at(x: Int, y: Int, w: Int, h: Int, spdx: Int, spdy: Int): Boolean {
        for (i in (max(0, (x / 8)) until (min(15, (x + w - 1) / 8)) + 1)) {
            for(j in (max(0, (y / 8)) until (min(15, (y + h - 1) / 8)) + 1)){
                val tile = tile_at(i, j)
                if ((tile == 17 && ((y + h - 1) % 8 >= 6 || y + h == j * 8 + 8) && spdy >= 0) ||
                        (tile == 27 && y % 8 <= 2 && spdy <= 0) ||
                        (tile == 43 && x % 8 <= 2 && spdx <= 0) ||
                        (tile == 59 && ((x + w - 1) % 8 >= 6 || x + w == i * 8 + 8) && spdx >= 0)) {
                    return true
                }
            }
        }
        return false
    }

    override fun map_data(): String {
        return "2331252548252532323232323300002425262425252631323232252628282824252525252525323328382828312525253232323233000000313232323232323232330000002432323233313232322525252525482525252525252526282824252548252525262828282824254825252526282828283132323225482525252525" +
                "252331323232332900002829000000242526313232332828002824262a102824254825252526002a2828292810244825282828290000000028282900000000002810000000372829000000002a2831482525252525482525323232332828242525254825323338282a283132252548252628382828282a2a2831323232322525" +
                "252523201028380000002a0000003d24252523201028292900282426003a382425252548253300002900002a0031252528382900003a676838280000000000003828393e003a2800000000000028002425253232323232332122222328282425252532332828282900002a283132252526282828282900002a28282838282448" +
                "3232332828282900000000003f2020244825262828290000002a243300002a2425322525260000000000000000003125290000000021222328280000000000002a2828343536290000000000002839242526212223202123313232332828242548262b000000000000001c00003b242526282828000000000028282828282425" +
                "2340283828293a2839000000343522252548262900000000000030000000002433003125333d3f00000000000000003100001c3a3a31252620283900000000000010282828290000000011113a2828313233242526103133202828282838242525262b000000000000000000003b2425262a2828670016002a28283828282425" +
                "263a282828102829000000000000312525323300000000110000370000003e2400000037212223000000000000000000395868282828242628290000000000002a2828290000000000002123283828292828313233282829002a002a2828242525332b0c00000011110000000c3b314826112810000000006828282828282425" +
                "252235353628280000000000003a282426003d003a3900270000000000002125001a000024252611111111000000002c28382828283831332800000017170000002a000000001111000024261028290028281b1b1b282800000000002a2125482628390000003b34362b000000002824252328283a67003a28282829002a3132" +
                "25333828282900000000000000283824252320201029003039000000005824480000003a31323235353536675800003c282828281028212329000000000000000000000000003436003a2426282800003828390000002a29000000000031323226101000000000282839000000002a2425332828283800282828390000001700" +
                "2600002a28000000003a283a2828282425252223283900372858390068283132000000282828282820202828283921222829002a28282426000000000000000000000000000020382828312523000000282828290000000000163a67682828003338280b00000010382800000b00003133282828282868282828280000001700" +
                "330000002867580000281028283422252525482628286720282828382828212200003a283828102900002a28382824252a0000002838242600000017170000000000000000002728282a283133390000282900000000000000002a28282829002a2839000000002a282900000000000028282838282828282828290000000000" +
                "0000003a2828383e3a2828283828242548252526002a282729002a28283432250000002a282828000000002810282425000000002a282426000000000000000000000000000037280000002a28283900280000003928390000000000282800000028290000002a2828000000000000002a282828281028282828675800000000" +
                "0000002838282821232800002a28242532322526003a2830000000002a28282400000000002a281111111128282824480000003a28283133000000000000171700013f0000002029000000003828000028013a28281028580000003a28290000002a280c0000003a380c00000000000c00002a2828282828292828290000003a" +
                "00013a2123282a313329001111112425002831263a3829300000000000002a310000000000002834222236292a0024253e013a3828292a00000000000000000035353536000020000000003d2a28671422222328282828283900582838283d00003a290000000028280000000000000000002a28282a29000058100012002a28" +
                "22222225262900212311112122222525002a3837282900301111110000003a2800013f0000002a282426290000002425222222232900000000000000171700002a282039003a2000003a003435353535252525222222232828282810282821220b10000000000b28100000000b0000002c00002838000000002a283917000028" +
                "2548252526111124252222252525482500012a2828673f242222230000003828222223000012002a24260000001224252525252600000000171700000000000000382028392827080028676820282828254825252525262a28282122222225253a28013d0000006828390000000000003c0168282800171717003a2800003a28" +
                "25252525252222252525252525252525222222222222222525482667586828282548260000270000242600000021252525254826171700000000000000000000002a2028102830003a282828202828282525252548252600002a2425252548252821222300000028282800000000000022222223286700000000282839002838" +
                "2532330000002432323232323232252525252628282828242532323232254825253232323232323225262828282448252525253300000000000000000000005225253232323233313232323233282900262829286700000000002828313232322525253233282800312525482525254825254826283828313232323232322548" +
                "26282800000030402a282828282824252548262838282831333828290031322526280000163a28283133282838242525482526000000000000000000000000522526000016000000002a10282838390026281a3820393d000000002a3828282825252628282829003b2425323232323232323233282828282828102828203125" +
                "3328390000003700002a3828002a2425252526282828282028292a0000002a313328111111282828000028002a312525252526000000000000000000000000522526000000001111000000292a28290026283a2820102011111121222328281025252628382800003b24262b002a2a38282828282829002a2800282838282831" +
                "28281029000000000000282839002448252526282900282067000000000000003810212223283829003a1029002a242532323367000000000000000000004200252639000000212300000000002122222522222321222321222324482628282832323328282800003b31332b00000028102829000000000029002a2828282900" +
                "2828280016000000162a2828280024252525262700002a2029000000000000002834252533292a0000002a00111124252223282800002c46472c00000042535325262800003a242600001600002425252525482631323331323324252620283822222328292867000028290000000000283800111100001200000028292a1600" +
                "283828000000000000003a28290024254825263700000029000000000000003a293b2426283900000000003b212225252526382867003c56573c4243435363633233283900282426111111111124252525482526201b1b1b1b1b24252628282825252600002a28143a2900000000000028293b21230000170000112867000000" +
                "2828286758000000586828380000313232323320000000000000000000272828003b2426290000000000003b312548252533282828392122222352535364000029002a28382831323535353522254825252525252300000000003132332810284825261111113435361111111100000000003b3133111111111127282900003b" +
                "2828282810290000002a28286700002835353536111100000000000011302838003b3133000000000000002a28313225262a282810282425252662636400000000160028282829000000000031322525252525252667580000002000002a28282525323535352222222222353639000000003b34353535353536303800000017" +
                "282900002a0000000000382a29003a282828283436200000000000002030282800002a29000011110000000028282831260029002a282448252523000000000039003a282900000000000000002831322525482526382900000017000058682832331028293b2448252526282828000000003b201b1b1b1b1b1b302800000017" +
                "283a0000000000000000280000002828283810292a000000000000002a3710281111111111112136000000002a28380b2600000000212525252526001c0000002828281000000000001100002a382829252525252628000000001700002a212228282908003b242525482628282912000000001b00000000000030290000003b" +
                "3829000000000000003a102900002838282828000000000000000000002a2828223535353535330000000000002828393300000000313225252533000000000028382829000000003b202b00682828003232323233290000000000000000312528280000003b3132322526382800170000000000000000110000370000000000" +
                "290000000000000000002a000000282928292a0000000000000000000000282a332838282829000000000000001028280000000042434424252628390000000028002a0000110000001b002a2010292c1b1b1b1b0000000000000000000010312829160000001b1b1b313328106700000000001100003a2700001b0000000000" +
                "00000100000011111100000000002a3a2a0000000000000000000000002a2800282829002a000000000000000028282800000000525354244826282800000000290000003b202b39000000002900003c000000000000000000000000000028282800000000000000001b1b2a2829000001000027390038300000000000000000" +
                "1111201111112122230000001212002a00010000000000000000000000002900290000000000000000002a6768282900003f01005253542425262810673a3900013f0000002a3829001100000000002101000000000000003a67000000002a382867586800000100000000682800000021230037282928300000000000000000" +
                "22222222222324482611111120201111002739000017170000001717000000000001000000001717000000282838393a0021222352535424253328282838290022232b00000828393b27000000001424230000001200000028290000000000282828102867001717171717282839000031333927101228370000000000000000" +
                "254825252526242526212222222222223a303800000000000000000000000000001717000000000000003a28282828280024252652535424262828282828283925262b00003a28103b30000000212225260000002700003a28000000000000282838282828390000005868283828000022233830281728270000000000000000" +
                "00000000000000008242525252528452339200001323232352232323232352230000000000000000b302000013232352526200a2828342525223232323232323" +
                "00000000000000a20182920013232352363636462535353545550000005525355284525262b20000000000004252525262828282425284525252845252525252" +
                "00000000000085868242845252525252b1006100b1b1b1b103b1b1b1b1b103b100000000000000111102000000a282425233000000a213233300009200008392" +
                "000000000000110000a2000000a28213000000002636363646550000005525355252528462b2a300000000004252845262828382132323232323232352528452" +
                "000000000000a201821323525284525200000000000000007300000000007300000000000000b343536300410000011362b2000000000000000000000000a200" +
                "0000000000b302b2002100000000a282000000000000000000560000005526365252522333b28292001111024252525262019200829200000000a28213525252" +
                "0000000000000000a2828242525252840000000000000000b10000000000b1000000000000000000b3435363930000b162273737373737373737374711000061" +
                "000000110000b100b302b20000006182000000000000000000000000005600005252338282828201a31222225252525262820000a20011111100008283425252" +
                "0000000000000093a382824252525252000061000011000000000011000000001100000000000000000000020182001152222222222222222222222232b20000" +
                "0000b302b200000000b10000000000a200000000000000009300000000000000846282828283828282132323528452526292000000112434440000a282425284" +
                "00000000000000a2828382428452525200000000b302b2936100b302b20061007293a30000000000000000b1a282931252845252525252232323232362b20000" +
                "000000b10000001100000000000000000000000093000086820000a3000000005262828201a200a282829200132323236211111111243535450000b312525252" +
                "00000000000000008282821323232323820000a300b1a382930000b100000000738283931100000000000011a382821323232323528462829200a20173b20061" +
                "000000000000b302b2000061000000000000a385828286828282828293000000526283829200000000a20000000000005222222232263636460000b342525252" +
                "00000011111111a3828201b1b1b1b1b182938282930082820000000000000000b100a282721100000000b372828283b122222232132333610000869200000000" +
                "00100000000000b1000000000000000086938282828201920000a20182a37686526282829300000000000000000000005252845252328283920000b342845252" +
                "00008612222232828382829300000000828282828283829200000000000061001100a382737200000000b373a2829211525284628382a2000000a20000000000" +
                "00021111111111111111111111110061828282a28382820000000000828282825262829200000000000000000000000052525252526201a2000000b342525252" +
                "00000113235252225353536300000000828300a282828201939300001100000072828292b1039300000000b100a282125223526292000000000000a300000000" +
                "0043535353535353535353535363b2008282920082829200061600a3828382a28462000000000000000000000000000052845252526292000011111142525252" +
                "0000a28282132362b1b1b1b1000000009200000000a28282828293b372b2000073820100110382a3000000110082821362101333610000000000008293000000" +
                "0002828382828202828282828272b20083820000a282d3000717f38282920000526200000000000093000000000000005252525284620000b312223213528452" +
                "000000828392b30300000000002100000000000000000082828282b303b20000b1a282837203820193000072a38292b162710000000000009300008382000000" +
                "00b1a282820182b1a28283a28273b200828293000082122232122232820000a3233300000000000082920000000000002323232323330000b342525232135252" +
                "000000a28200b37300000000a37200000010000000111111118283b373b200a30000828273039200828300738283001162930000000000008200008282920000" +
                "0000009261a28200008261008282000001920000000213233342846282243434000000000000000082000085860000008382829200000000b342528452321323" +
                "0000100082000082000000a2820300002222321111125353630182829200008300009200b1030000a28200008282001262829200000000a38292008282000000" +
                "00858600008282a3828293008292610082001000001222222252525232253535000000f3100000a3820000a2010000008292000000009300b342525252522222" +
                "0400122232b200839321008683039300528452222262c000a28282820000a38210000000a3738000008293008292001362820000000000828300a38201000000" +
                "00a282828292a2828283828282000000343434344442528452525252622535350000001263000083829300008200c1008210d3e300a38200b342525252845252" +
                "1232425262b28682827282820103820052525252846200000082829200008282320000008382930000a28201820000b162839300000000828200828282930000" +
                "0000008382000000a28201820000000035353535454252525252528462253535000000032444008282820000829300002222223201828393b342525252525252" +
                "525252525262b2b1b1b1132323526200845223232323232352522323233382825252525252525252525284522333b2822323232323526282820000b342525252" +
                "52845252525252848452525262838242528452522333828292425223232352520000000000000000000000000000000000000000000000000000000000000000" +
                "525252845262b2000000b1b1b142620023338276000000824233b2a282018283525252845252232323235262b1b10083921000a382426283920000b342232323" +
                "2323232323232323232323526201821352522333b1b1018241133383828242840000000000000000000000000000000000000000000000000000000000000000" +
                "525252525262b20000000000a242627682828392000011a273b200a382729200525252525233b1b1b1b11333000000825353536382426282410000b30382a2a2" +
                "a1829200a2828382820182426200a2835262b1b10000831232b2000080014252000000000000a300000000000000000000000000000000000000000000000000" +
                "528452232333b20000001100824262928201a20000b3720092000000830300002323525262b200000000b3720000a382828283828242522232b200b373928000" +
                "000100110092a2829211a2133300a3825262b2000000a21333b20000868242520000000000000100009300000000000000000000000000000000000000000000" +
                "525262122232b200a37672b2a24262838292000000b30300000000a3820300002232132333b200000000b303829300a2838292019242845262b2000000000000" +
                "00a2b302b2a36182b302b200110000825262b200000000b1b10000a283a2425200000000a30082000083000000000000000000000094a4b4c4d4e4f400000000" +
                "525262428462b200a28303b2214262928300000000b3030000000000a203e3415252222232b200000000b30392000000829200000042525262b2000000000000" +
                "000000b100a2828200b100b302b211a25262b200000000000000000092b3428400000000827682000001009300000000000000000095a5b5c5d5e5f500000000" +
                "232333132362b221008203b2711333008293858693b3031111111111114222225252845262b200001100b303b2000000821111111142528462b2000000000000" +
                "000000000000110176851100b1b3026184621111111100000061000000b3135200000000828382670082768200000000000000000096a6b6c6d6e6f600000000" +
                "82000000a203117200a203b200010193828283824353235353535353535252845252525262b200b37200b303b2000000824353535323235262b2000011000000" +
                "0000000000b30282828372b26100b100525232122232b200000000000000b14200000000a28282123282839200000000000000000097a7b7c7d7e7f700000000" +
                "9200110000135362b2001353535353539200a2000001828282829200b34252522323232362b261b30300b3030000000092b1b1b1b1b1b34262b200b372b20000" +
                "001100000000b1a2828273b200000000232333132333b200001111000000b342000000868382125252328293a300000000000000000000000000000000000000" +
                "00b372b200a28303b2000000a28293b3000000000000a2828382827612525252b1b1b1b173b200b30393b30361000000000000000000b34262b271b303b20000" +
                "b302b211000000110092b100000000a3b1b1b1b1b1b10011111232110000b342000000a282125284525232828386000000000000000000000000000000000000" +
                "80b303b20000820311111111008283b311111111110000829200928242528452000000a3820000b30382b37300000000000000000000b3426211111103b20000" +
                "00b1b302b200b372b200000000000082b21000000000b31222522363b200b3138585868292425252525262018282860000000000000000000000000000000000" +
                "00b373b20000a21353535363008292b32222222232111102b20000a21323525200000001839200b3038282820000000011111111930011425222222233b20000" +
                "100000b10000b303b200000000858682b27100000000b3425233b1b1000000b182018283001323525284629200a2820000000000000000000000000000000000" +
                "9300b100000000b1b1b1b1b100a200b323232323235363b100000000b1b1135200000000820000b30382839200000000222222328283432323232333b2000000" +
                "329300000000b373b200000000a20182111111110000b31333b100a30061000000a28293f3123242522333020000820000000000000000000000000000000000" +
                "829200001000410000000000000000b39310d30000a28200000000000000824200000086827600b30300a282760000005252526200828200a30182a2006100a3" +
                "62820000000000b100000093a382838222222232b20000b1b1000083000000860000122222526213331222328293827600000000000000000000000000000000" +
                "017685a31222321111111111002100b322223293000182930000000080a301131000a383829200b373000083920000005284526200a282828283920000000082" +
                "62839321000000000000a3828282820152845262b261000093000082a300a3821000135252845222225252523201838200000000000000000000000000000000" +
                "828382824252522222222232007100b352526282a38283820000000000838282320001828200000083000082010000005252526271718283820000000000a382" +
                "628201729300000000a282828382828252528462b20000a38300a382018283821222324252525252525284525222223200000000000000000000000000000000"
    }

    override fun flag_data(): String {
        return "0000000000000000000000000000000004020000000000000000000200000000030303030303030304040402020000000303030303030303040404020202020200001313131302020302020202020002000013131313020204020202020202020000131313130004040202020202020200001313131300000002020202020202" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    }

    /**

    1: self.player_spawn,
    8: self.key,
    11: self.platform,
    12: self.platform,
    18: self.spring,
    20: self.chest,
    22: self.balloon,
    23: self.fall_floor,
    26: self.fruit,
    28: self.fly_fruit,
    64: self.fake_wall,
    #86: self.message,
    #96: self.big_chest,
    #118: self.flag
     */
    fun getString(): String {
        val spikes: HashMap<Int, String> = HashMap()
        spikes[17] = "^^"
        spikes[27] = "vv"
        spikes[43] = "> "
        spikes[59] = " <"

        val objs: HashMap<Class<*>?, String> = HashMap()
        objs[spring::class.java] = "ΞΞ"
        objs[fall_floor::class.java] = "▒▒"
        objs[balloon::class.java] = "()"
        objs[key::class.java] = "¤¬"
        objs[chest::class.java] = "╔╗"
        objs[fruit::class.java] = "{}"
        objs[fly_fruit::class.java] = "{}"
        objs[fake_wall::class.java] = "▓▓"
        objs[platform::class.java] = "oo"
        objs[player::class.java] = "\uD83E\uDD7A"
        objs[player_spawn::class.java] = ":3"
        //# init map
        val map_str = ArrayList<String>()
        for(i in 0 until (16 * 2) * 16){
            map_str.add("  ")
        }
        for (tx in 0 until 16){
            for(ty in 0 until 16){
                val pos = tx + 17 * ty
                val tile = p8.mget(room.x * 16 + tx, room.y * 16 + ty)
                if(tile?.let { p8.fget(it, 4) } == 1) {
                    map_str[pos] = "░░"
                }else if(tile?.let { p8.fget(it, 0) } == 1) {
                    map_str[pos] = "██"
                }else if(tile in spikes) {
                    map_str[pos] = spikes[tile]!!
                }
            }
        }

        for (o in objects){
            if(o::class.java in objs.keys){
                if(o.spr == 0)
                    continue
                val ox = round(o.x / 8)
                val oy = round(o.y / 8)
                val pos = ox + 17 * oy
                if(ox in 0.0..15.0 && oy >= 0 && oy <= 15){
                    map_str[(pos).toInt()] = objs[o::class.java]!!
                    if(o::class.java == platform::class.java && ox+1 <= 15){
                        map_str[(pos+1).toInt()] = objs[o::class.java]!!
                    }else if(o::class.java == fly_fruit::class.java){
                        if(ox-1 >= 0) map_str[(pos-1).toInt()] = " »"
                        if(ox+1 <= 15) map_str[(pos+1).toInt()] = "« "
                    }else if(o::class.java == fake_wall::class.java){
                        if(ox+1 <= 15) map_str[(pos + 1).toInt()] = objs[o::class.java]!!
                        if(oy+1 <= 15) map_str[(pos + 17).toInt()] = objs[o::class.java]!!
                        if((ox+1 <= 15) && (oy+1) <= 15) map_str[(pos+18).toInt()] = objs[o::class.java]!!
                    }
                }
            }
        }

        var str = ""
        for(y in 0 until 16){
            for(x in 0 until 16){
                str += map_str[x + 17 * y]
            }
            str += "\n"
        }

        return str
    }
}