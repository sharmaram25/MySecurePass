package com.mysecurepass.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.mysecurepass.container

/** Quick Settings tile: one tap generates a password with your saved options and copies it. */
class GenerateTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        val c = container
        val generated = c.generator.generate(c.settings.generator.value)
        val seconds = c.settings.settings.value.clipboardClearSeconds
        c.clipboard.copy(generated.value, seconds)
        if (!c.clipboard.systemShowsConfirmation) {
            Toast.makeText(this, if (seconds > 0) "Password copied · clears in ${seconds}s" else "Password copied", Toast.LENGTH_SHORT).show()
        }
    }
}
