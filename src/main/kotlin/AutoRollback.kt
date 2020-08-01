import arc.ApplicationListener
import arc.Core
import arc.files.Fi
import arc.util.CommandHandler
import arc.util.Log
import mindustry.Vars.*
import mindustry.core.GameState
import mindustry.entities.type.Player
import mindustry.gen.Call
import mindustry.io.SaveIO
import mindustry.plugin.Plugin
import java.util.*

class AutoRollback : Plugin() {
    val timer = Timer()
    val file : Fi = saveDirectory.child("rollback.$saveExtension")
    val voted = arc.struct.Array<String>()
    var isVoting = false
    var require = 0

    override fun init() {
        val task = object : TimerTask() {
            override fun run() {
                save()
            }
        }
        timer.scheduleAtFixedRate(task, 300000, 300000)

        Core.app.addListener(object : ApplicationListener {
            var tick = 0

            override fun update() {
                require = if (playerGroup.size() > 8) 6 else 2 + if (playerGroup.size() > 4) 1 else 0

                if(isVoting && voted.size >= require){
                    rollback()
                    voted.clear()
                    tick = 0
                    isVoting = false
                } else if(isVoting && voted.size < require){
                    tick++
                    if(tick >= 3600){
                        voted.clear()
                        Call.sendMessage("Rollback vote failed.")
                        isVoting = false
                        tick = 0
                    }
                }
            }

            override fun dispose() {
                timer.cancel()
            }
        })
    }

    fun rollback(){
        val players = arc.struct.Array<Player>()
        for (p in playerGroup.all()) {
            players.add(p)
            p.isDead = true
        }
        logic.reset()
        Call.onWorldDataBegin()
        try {
            SaveIO.load(file)
            logic.play()
            for (p in players) {
                if (p.con == null) continue
                p.reset()
                if (state.rules.pvp) {
                    p.team = netServer.assignTeam(p, arc.struct.Array.ArrayIterable(players))
                }
                netServer.sendWorldData(p)
            }
        } catch (e: SaveIO.SaveException) {
            e.printStackTrace()
        }
        Log.info("Map rollbacked.")
        Thread {
            try {
                val orignal = state.rules.respawnTime
                state.rules.respawnTime = 0f
                Call.onSetRules(state.rules)
                Thread.sleep(3000)
                state.rules.respawnTime = orignal
                Call.onSetRules(state.rules)
            } catch (ignored: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.start()
        if (state.`is`(GameState.State.playing)) Call.sendMessage("[green]Map rollbacked.")
    }

    fun save(){
        Core.app.post { SaveIO.save(file) }
    }

    override fun registerClientCommands(handler: CommandHandler) {
        handler.register("rollback", "Return the server to 5 minutes ago.") { _: Array<String>, p: Player ->
            if(playerGroup.size() > 4) {
                if (!isVoting) {
                    isVoting = true
                }
                if (!voted.contains(p.uuid)) voted.add(p.uuid)
                if (voted.size <= require) Call.sendMessage("Rollback vote remaining: ${voted.size}/" + if (playerGroup.size() > 8) 6 else 2 + if (playerGroup.size() > 4) 1 else 0)
            } else {
                p.sendMessage("There must be at least 4 players in the rollback vote.")
            }
        }
    }
}