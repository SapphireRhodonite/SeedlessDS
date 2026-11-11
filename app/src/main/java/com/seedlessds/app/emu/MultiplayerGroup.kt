package com.seedlessds.app.emu

import java.net.Inet4Address
import java.net.InetAddress

internal data class MultiplayerGroup(
    val name: String,
    val interfaceName: String,
    val index: Int,
    val local: Inet4Address,
    val owner: Inet4Address,
) {
    fun admits(mode: Int, address: String): Boolean =
        (mode == 1 || mode == 2) && (mode == 1) == (local == owner) &&
            (address.isEmpty() || address == owner.hostAddress)

    companion object {
        fun resolve(name: String?, expectedName: String?, formed: Boolean, hosting: Boolean,
            groupOwner: Boolean, connectionOwner: Boolean, owner: InetAddress?, interfaceName: String?,
            index: Int, addresses: List<Pair<InetAddress, Int>>): MultiplayerGroup? {
            if (name.isNullOrBlank() || (expectedName != null && name != expectedName) || !formed ||
                groupOwner != hosting || connectionOwner != hosting || interfaceName.isNullOrBlank() || index <= 0 ||
                owner !is Inet4Address || !unicast(owner)) return null
            val local = addresses.singleOrNull { (candidate, prefix) ->
                candidate is Inet4Address && unicast(candidate) && (candidate == owner) == hosting &&
                    prefix in 1..32 && ((bits(candidate) xor bits(owner)) ushr (32 - prefix)) == 0
            }?.first as? Inet4Address ?: return null
            return MultiplayerGroup(name, interfaceName, index, local, owner)
        }

        private fun unicast(address: Inet4Address): Boolean =
            !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isMulticastAddress &&
                (address.address[0].toInt() and 255) in 1..223

        private fun bits(address: Inet4Address): Int =
            address.address.fold(0) { result, byte -> (result shl 8) or (byte.toInt() and 255) }
    }
}
