
import arc.ApplicationListener
import arc.Core
import arc.files.Fi
import arc.struct.Seq
import arc.util.CommandHandler
import arc.util.Log
import mindustry.Vars.*
import mindustry.core.GameState
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.io.SaveIO
import mindustry.mod.Plugin
import java.util.*

class AutoRollback : Plugin() {
    val timer = Timer()
    val file : Fi = saveDirectory.child("rollback.$saveExtension")
    val voted = Seq<String>()
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
                require = if (Groups.player.size() > 8) 6 else 2 + if (Groups.player.size() > 4) 1 else 0

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
        val players = Seq<Player>()
        for (p in Groups.player) {
            players.add(p)
            p.unit().kill()
        }

        Core.app.post {
            logic.reset()
            Call.worldDataBegin()
            try {
                SaveIO.load(file)
                logic.play()
                for (p in players) {
                    if (p.con() == null) continue
                    p.reset()
                    if (state.rules.pvp) {
                        p.team(netServer.assignTeam(p, Seq.SeqIterable(players)))
                    }
                    netServer.sendWorldData(p)
                }
            } catch (e: SaveIO.SaveException) {
                e.printStackTrace()
            }
            Log.info("Map rollbacked.")
            if (state.`is`(GameState.State.playing)) Call.sendMessage("[green]Map rollbacked.")
        }
    }

    fun save(){
        Core.app.post { SaveIO.save(file) }
    }

    override fun registerClientCommands(handler: CommandHandler) {
        handler.register("rollback", "Return the server to 5 minutes ago.") { _: Array<String>, p: Player ->
            if(Groups.player.size() > 4) {
                if (!isVoting) isVoting = true
                if (!voted.contains(p.uuid())) voted.add(p.uuid())
                if (voted.size <= require) Call.sendMessage("Rollback vote remaining: ${voted.size}/" + if (Groups.player.size() > 8) 6 else 2 + if (Groups.player.size() > 4) 1 else 0)
            } else {
                p.sendMessage("There must be at least 4 players in the rollback vote.")
            }
        }
    }
}