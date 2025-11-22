package com.example.vitruvianredux.data.ble

/**
 * Represents the state of the device handle.
 */
enum class HandleState {
    /**
     * The handle is released (not being held).
     */
    Released,

    /**
     * The handle is grabbed (being held stationary).
     */
    Grabbed,

    /**
     * The handle is moving (active exercise movement).
     */
    Moving
}