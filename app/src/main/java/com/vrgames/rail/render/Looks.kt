package com.vrgames.rail.render

/**
 * How big each model stands on the board. The models come normalized to 1 m tall; a cell is 8 cm.
 * The turns say which way a model faces at yaw 0: Seed3D's frontal models face +x.
 */
object Looks {
    private val scales = mapOf(
        "loco" to 0.03f, "tender" to 0.028f, "freight" to 0.028f, "tanker" to 0.028f, "coach" to 0.0165f,
        "station" to 0.05f, "tower" to 0.09f, "house1" to 0.07f, "house2" to 0.07f, "factory" to 0.08f, "mine" to 0.08f, "farm" to 0.075f, "tree" to 0.07f,
    )

    fun scale(name: String) = scales[name] ?: 0.05f
    fun height(name: String) = scale(name)
    /** The locomotive's model faces +z; the rest of the rolling stock lies along x. */
    fun turn(name: String) = if (name == "loco") 90f else 0f

    /** What to add to a +x-facing yaw for buildings whose door looks along +z instead (from their renders). */
    fun doorTurn(name: String) = if (name == "house2" || name == "factory" || name == "mine") 90f else 0f

    const val ROCK = 0.05f
    const val TUNNEL = 0.05f
    /** Its mouth looks along +z. */
    const val TUNNEL_TURN = 90f
    /** The bridge's model runs along z, about 4.4 long, 0.8 wide, 1 high. */
    const val BRIDGE_X = 0.0375f
    const val BRIDGE_Y = 0.019f
    const val BRIDGE_Z = 0.0227f
    const val BRIDGE_TURN = 90f
}
