import kotlin.math.pow

class Pico8 {
    var btn_state = 0
    var memory : HashMap<String, IntArray> = HashMap()
    lateinit var cart : Cart

    fun btn(i: Number): Boolean {
        return ((btn_state and ((1L shl i.toInt()).toInt()) != 0))
    }

    fun mset(x: Number, y: Number, tile: Number) {
        memory["map"]?.set(x.toInt() + y.toInt() * 128, tile.toInt());
    }

    fun mget(x: Number, y: Number) : Int? {
        return memory["map"]?.get(x.toInt() + y.toInt() * 128);
    }

    fun fget(n: Number, f: Number? = null) : Int? {
        val flags = memory["flags"]?.get(n.toInt());
        if (flags != null) {
            return if (f == null) flags else (flags and 2.0.pow(f.toDouble()).toInt() != 0).toInt()
        }
        return null
    }

    fun load_game(cart: Cart){
        this.cart = cart
        memory["map"] = IntArray(8192)
        println(cart.map_data().length)
        for (i in 0 until cart.map_data().length step 2){
            if(i < 8192) {
                memory["map"]?.set(i/2, Integer.parseInt(cart.map_data()[i].toString() + cart.map_data()[i+1], 16))
            }else{
                memory["map"]?.set(i/2, Integer.parseInt(cart.map_data()[i+1].toString() + cart.map_data()[i], 16))
            }
        }
        memory["flags"] = IntArray(cart.flag_data().length/2)
        for(i in 0 until cart.flag_data().length step 2){
            memory["flags"]?.set(i/2, Integer.parseInt(cart.flag_data()[i].toString() + cart.flag_data()[i+1], 16))
        }
        cart.init()
    }

    fun reset(){
        load_game(cart)
    }

    fun step(){
        cart.update()
        cart.draw()
    }

    fun Boolean.toInt() = if (this) 1 else 0

    fun set_inputs(l: Boolean = false, r: Boolean = false, u: Boolean = false, d: Boolean = false, z: Boolean = false, x: Boolean = false){
        set_btn_state(l.toInt() * 1 + r.toInt() * 2 + u.toInt() * 4 + d.toInt() * 8 + z.toInt() * 16 + x.toInt() * 32)
    }

    fun set_btn_state (state: Int){
        btn_state = state
    }
}

/**
 *   def __init__(self, cart):
self._btn_state = 0
self.load_game(cart)

# game functions

def btn(self, i):
return self._btn_state & (2 ** i) != 0

def mset(self, x, y, tile):
self._memory['map'][x + y * 128] = tile

def mget(self, x, y):
return self._memory['map'][x + y * 128]

def fget(self, n, f=None):
flags = self._memory['flags'][n]
return flags if f == None else flags & 2 ** f != 0

# console commands

# load game from cart
def load_game(self, cart):
self._cart = cart
self._game = self._cart(self)
self._memory = {
'map': [int(self._game.map_data[i:i + 2][::1 if i < 8192 else -1], 16) for i in range(0, len(self._game.map_data), 2)],
'flags': [int(self._game.flag_data[i:i + 2], 16) for i in range(0, len(self._game.flag_data), 2)]
}
self._game._init()

# reload the current cart
def reset(self):
self.load_game(self._cart)

# perform a game step
def step(self):
self._game._update()
self._game._draw()

# set button state from inputs
def set_inputs(self, l=False, r=False, u=False, d=False, z=False, x=False):
self.set_btn_state(l * 1 + r * 2 + u * 4 + d * 8 + z * 16 + x * 32)

# set button state directly (0bxzdurl)
def set_btn_state(self, state):
self._btn_state = state

@property
def game(self):
return self._game

@property
def input_display(self):
l, r, u, d, z, x = ('▓▓' if self.btn(i) else '░░' for i in range(6))
return f"        {u}\n{z}{x}  {l}{d}{r}"
 */