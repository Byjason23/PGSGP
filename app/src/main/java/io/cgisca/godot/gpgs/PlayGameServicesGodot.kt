package io.cgisca.godot.gpgs

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.drive.Drive
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.SnapshotMetadata
import io.cgisca.godot.gpgs.achievements.AchievementsController
import io.cgisca.godot.gpgs.achievements.AchievementsListener
import io.cgisca.godot.gpgs.events.EventsController
import io.cgisca.godot.gpgs.events.EventsListener
import io.cgisca.godot.gpgs.leaderboards.LeaderBoardsListener
import io.cgisca.godot.gpgs.leaderboards.LeaderboardsController
import io.cgisca.godot.gpgs.savedgames.SavedGamesController
import io.cgisca.godot.gpgs.savedgames.SavedGamesListener
import io.cgisca.godot.gpgs.signin.SignInController
import io.cgisca.godot.gpgs.signin.SignInListener
import io.cgisca.godot.gpgs.stats.PlayerStatsController
import io.cgisca.godot.gpgs.stats.PlayerStatsListener
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import java.math.BigInteger
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.Random

class PlayGameServicesGodot(godot: Godot) : GodotPlugin(godot), AchievementsListener, EventsListener,
    LeaderBoardsListener, SavedGamesListener, SignInListener, PlayerStatsListener {

    private lateinit var connectionController: ConnectionController
    private lateinit var signInController: SignInController
    private lateinit var achievementsController: AchievementsController
    private lateinit var leaderboardsController: LeaderboardsController
    private lateinit var eventsController: EventsController
    private lateinit var playerStatsController: PlayerStatsController
    private lateinit var savedGamesController: SavedGamesController
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var saveGameName: String

    companion object {
        val SIGNAL_SIGN_IN_SUCCESSFUL = SignalInfo("_on_sign_in_success", String::class.java)
        val SIGNAL_SIGN_IN_FAILED = SignalInfo("_on_sign_in_failed", Int::class.javaObjectType)
        val SIGNAL_SIGN_OUT_SUCCESS = SignalInfo("_on_sign_out_success")
        val SIGNAL_SIGN_OUT_FAILED = SignalInfo("_on_sign_out_failed")
        val SIGNAL_ACHIEVEMENT_UNLOCKED = SignalInfo("_on_achievement_unlocked", String::class.java)
        val SIGNAL_ACHIEVEMENT_UNLOCKED_FAILED = SignalInfo("_on_achievement_unlocking_failed", String::class.java)
        val SIGNAL_ACHIEVEMENT_REVEALED = SignalInfo("_on_achievement_revealed", String::class.java)
        val SIGNAL_ACHIEVEMENT_REVEALED_FAILED = SignalInfo("_on_achievement_revealing_failed", String::class.java)
        val SIGNAL_ACHIEVEMENT_INCREMENTED = SignalInfo("_on_achievement_incremented", String::class.java)
        val SIGNAL_ACHIEVEMENT_INCREMENTED_FAILED =
            SignalInfo("_on_achievement_incrementing_failed", String::class.java)
        val SIGNAL_ACHIEVEMENT_STEPS_SET = SignalInfo("_on_achievement_steps_set", String::class.java)
        val SIGNAL_ACHIEVEMENT_STEPS_SET_FAILED =
            SignalInfo("_on_achievement_steps_setting_failed", String::class.java)
        val SIGNAL_LEADERBOARD_SCORE_SUBMITTED = SignalInfo("_on_leaderboard_score_submitted", String::class.java)
        val SIGNAL_LEADERBOARD_SCORE_SUBMITTED_FAILED =
            SignalInfo("_on_leaderboard_score_submitting_failed", String::class.java)
        val SIGNAL_EVENT_SUBMITTED = SignalInfo("_on_event_submitted", String::class.java)
        val SIGNAL_EVENT_SUBMITTED_FAILED = SignalInfo("_on_event_submitting_failed", String::class.java)
        val SIGNAL_EVENTS_LOADED = SignalInfo("_on_events_loaded", String::class.java)
        val SIGNAL_EVENTS_EMPTY = SignalInfo("_on_events_empty")
        val SIGNAL_EVENTS_LOADED_FAILED = SignalInfo("_on_events_loading_failed")
        val SIGNAL_PLAYER_STATS_LOADED = SignalInfo("_on_player_stats_loaded", String::class.java)
        val SIGNAL_PLAYER_STATS_LOADED_FAILED = SignalInfo("_on_player_stats_loading_failed")
        val SIGNAL_SAVED_GAME_SUCCESS = SignalInfo("_on_game_saved_success")
        val SIGNAL_SAVED_GAME_FAILED = SignalInfo("_on_game_saved_fail")
        val SIGNAL_SAVED_GAME_LOAD_SUCCESS = SignalInfo("_on_game_load_success", String::class.java)
        val SIGNAL_SAVED_GAME_LOAD_FAIL = SignalInfo("_on_game_load_fail")
        val SIGNAL_SAVED_GAME_CREATE_SNAPSHOT = SignalInfo("_on_create_new_snapshot", String::class.java)
    }

    override fun getPluginName(): String {
        return BuildConfig.LIBRARY_NAME
    }

    override fun getPluginMethods(): MutableList<String> {
        return mutableListOf(
            "init",
            "initWithSavedGames",
            "signIn",
            "signOut",
            "isSignedIn",
            "showAchievements",
            "unlockAchievement",
            "revealAchievement",
            "incrementAchievement",
            "setAchievementSteps",
            "showLeaderBoard",
            "showAllLeaderBoards",
            "submitLeaderBoardScore",
            "submitEvent",
            "loadEvents",
            "loadEventsById",
            "loadPlayerStats",
            "showSavedGames",
            "saveSnapshot",
            "loadSnapshot"
        )
    }

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SIGNAL_SIGN_IN_SUCCESSFUL,
            SIGNAL_SIGN_IN_FAILED,
            SIGNAL_SIGN_OUT_SUCCESS,
            SIGNAL_SIGN_OUT_FAILED,
            SIGNAL_ACHIEVEMENT_UNLOCKED,
            SIGNAL_ACHIEVEMENT_UNLOCKED_FAILED,
            SIGNAL_ACHIEVEMENT_REVEALED,
            SIGNAL_ACHIEVEMENT_REVEALED_FAILED,
            SIGNAL_ACHIEVEMENT_INCREMENTED,
            SIGNAL_ACHIEVEMENT_INCREMENTED_FAILED,
            SIGNAL_ACHIEVEMENT_STEPS_SET,
            SIGNAL_ACHIEVEMENT_STEPS_SET_FAILED,
            SIGNAL_LEADERBOARD_SCORE_SUBMITTED,
            SIGNAL_LEADERBOARD_SCORE_SUBMITTED_FAILED,
            SIGNAL_EVENT_SUBMITTED,
            SIGNAL_EVENT_SUBMITTED_FAILED,
            SIGNAL_EVENTS_LOADED,
            SIGNAL_EVENTS_EMPTY,
            SIGNAL_EVENTS_LOADED_FAILED,
            SIGNAL_PLAYER_STATS_LOADED,
            SIGNAL_PLAYER_STATS_LOADED_FAILED,
            SIGNAL_SAVED_GAME_SUCCESS,
            SIGNAL_SAVED_GAME_FAILED,
            SIGNAL_SAVED_GAME_LOAD_SUCCESS,
            SIGNAL_SAVED_GAME_LOAD_FAIL,
            SIGNAL_SAVED_GAME_CREATE_SNAPSHOT
        )
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SignInController.RC_SIGN_IN) {
            val googleSignInResult = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            signInController.onSignInActivityResult(googleSignInResult)
        } else if (requestCode == SavedGamesController.RC_SAVED_GAMES) {
            if (data != null) {
                if (data.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_METADATA)) {
                    data.getParcelableExtra<SnapshotMetadata>(SnapshotsClient.EXTRA_SNAPSHOT_METADATA)?.let {
                        savedGamesController.loadSnapshot(it.uniqueName)
                    }
                } else if (data.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_NEW)) {
                    val unique = BigInteger(281, Random()).toString(13)
                    savedGamesController.createNewSnapshot("$saveGameName$unique")
                }
            }
        }
    }

    fun init(enablePopups: Boolean) {
        initialize(false, enablePopups, "DefaultGame")
    }

    fun initWithSavedGames(enablePopups: Boolean, saveGameName: String) {
        initialize(true, enablePopups, saveGameName)
    }

    private fun initialize(enableSaveGamesFunctionality: Boolean, enablePopups: Boolean, saveGameName: String) {
        this.saveGameName = saveGameName
        val signInOptions = if (enableSaveGamesFunctionality) {
            val signInOptionsBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
            signInOptionsBuilder.requestScopes(Drive.SCOPE_APPFOLDER).requestId()
            signInOptionsBuilder.build()
        } else {
            GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN
        }

        connectionController = ConnectionController(godot as Activity, signInOptions)
        signInController = SignInController(godot as Activity, this, connectionController)
        achievementsController = AchievementsController(godot as Activity, this, connectionController)
        leaderboardsController = LeaderboardsController(godot as Activity, this, connectionController)
        eventsController = EventsController(godot as Activity, this, connectionController)
        playerStatsController = PlayerStatsController(godot as Activity, this, connectionController)
        savedGamesController = SavedGamesController(godot as Activity, this, connectionController)

        googleSignInClient = GoogleSignIn.getClient(godot as Activity, signInOptions)

        runOnUiThread {
            signInController.setShowPopups(enablePopups)
        }
    }

    fun signIn() {
        runOnUiThread {
            signInController.signIn(googleSignInClient)
        }
    }

    fun signOut() {
        runOnUiThread {
            signInController.signOut(googleSignInClient)
        }
    }

    fun isSignedIn(): Boolean {
        return signInController.isSignedIn()

    fun showAchievements() {
        runOnUiThread {
            achievementsController.showAchievements()
        }
    }

    fun unlockAchievement(achievementName: String) {
        runOnUiThread {
            achievementsController.unlockAchievement(achievementName)
        }
    }

    fun revealAchievement(achievementName: String) {
        runOnUiThread {
            achievementsController.revealAchievement(achievementName)
        }
    }

    fun incrementAchievement(achievementName: String, step: Int) {
        runOnUiThread {
            achievementsController.incrementAchievement(achievementName, step)
        }
    }

    fun setAchievementSteps(achievementName: String, steps: Int) {
        runOnUiThread {
            achievementsController.setAchievementSteps(achievementName, steps)
        }
    }

    fun showLeaderBoard(leaderBoardId: String) {
        runOnUiThread {
            leaderboardsController.showLeaderboard(leaderBoardId)
        }
    }

    fun showAllLeaderBoards() {
        runOnUiThread {
            leaderboardsController.showAllLeaderboards()
        }
    }

    fun submitLeaderBoardScore(leaderBoardId: String, score: Int) {
        runOnUiThread {
            leaderboardsController.submitScore(leaderBoardId, score)
        }
    }

    fun submitEvent(eventId: String, incrementBy: Int) {
        runOnUiThread {
            eventsController.submitEvent(eventId, incrementBy)
        }
    }

    fun loadEvents() {
        runOnUiThread {
            eventsController.loadEvents()
        }
    }

    fun loadEventsById(ids: Array<String>) {
        runOnUiThread {
            eventsController.loadEventById(ids)
        }
    }

    fun loadPlayerStats(forceRefresh: Boolean) {
        runOnUiThread {
            playerStatsController.checkPlayerStats(forceRefresh)
        }
    }

    fun showSavedGames(title: String, allowAdBtn: Boolean, allowDeleteBtn: Boolean, maxNumberOfSavedGamesToShow: Int) {
        runOnUiThread {
            savedGamesController.showSavedGamesUI(title, allowAdBtn, allowDeleteBtn, maxNumberOfSavedGamesToShow)
        }
    }

    fun saveSnapshot(name: String, data: String, description: String) {
        runOnUiThread {
            savedGamesController.saveSnapshot(name, data, description)
        }
    }

    fun loadSnapshot(name: String) {
        runOnUiThread {
            savedGamesController.loadSnapshot(name)
        }
    }

    override fun onAchievementUnlocked(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_UNLOCKED.name, achievementName)
    }

    override fun onAchievementUnlockingFailed(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_UNLOCKED_FAILED.name, achievementName)
    }

    override fun onAchievementRevealed(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_REVEALED.name, achievementName)
    }

    override fun onAchievementRevealingFailed(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_REVEALED_FAILED.name, achievementName)
    }

    override fun onAchievementIncremented(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_INCREMENTED.name, achievementName)
    }

    override fun onAchievementIncrementingFailed(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_INCREMENTED_FAILED.name, achievementName)
    }

    override fun onAchievementStepsSet(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_STEPS_SET.name, achievementName)
    }

    override fun onAchievementStepsSettingFailed(achievementName: String) {
        emitSignal(SIGNAL_ACHIEVEMENT_STEPS_SET_FAILED.name, achievementName)
    }

    override fun onEventSubmitted(eventId: String) {
        emitSignal(SIGNAL_EVENT_SUBMITTED.name, eventId)
    }

    override fun onEventSubmittingFailed(eventId: String) {
        emitSignal(SIGNAL_EVENT_SUBMITTED_FAILED.name, eventId)
    }

    override fun onEventsLoaded(eventsJson: String) {
        emitSignal(SIGNAL_EVENTS_LOADED.name, eventsJson)
    }

    override fun onEventsEmpty() {
        emitSignal(SIGNAL_EVENTS_EMPTY.name)
    }

    override fun onEventsLoadingFailed() {
        emitSignal(SIGNAL_EVENTS_LOADED_FAILED.name)
    }

    override fun onLeaderBoardScoreSubmitted(leaderboardId: String) {
        emitSignal(SIGNAL_LEADERBOARD_SCORE_SUBMITTED.name, leaderboardId)
    }

    override fun onLeaderBoardScoreSubmittingFailed(leaderboardId: String) {
        emitSignal(SIGNAL_LEADERBOARD_SCORE_SUBMITTED_FAILED.name, leaderboardId)
    }

    override fun onSavedGameSuccess() {
        emitSignal(SIGNAL_SAVED_GAME_SUCCESS.name)
    }

    override fun onSavedGameFailed() {
        emitSignal(SIGNAL_SAVED_GAME_FAILED.name)
    }

    override fun onSavedGameLoadFailed() {
        emitSignal(SIGNAL_SAVED_GAME_LOAD_FAIL.name)
    }

    override fun onSavedGameLoadSuccess(data: String) {
        emitSignal(SIGNAL_SAVED_GAME_LOAD_SUCCESS.name, data)
    }

    override fun onSavedGameCreateSnapshot(currentSaveName: String) {
        emitSignal(SIGNAL_SAVED_GAME_CREATE_SNAPSHOT.name, currentSaveName)
    }

    override fun onSignedInSuccessfully(accountId: String) {
        emitSignal(SIGNAL_SIGN_IN_SUCCESSFUL.name, accountId)
    }

    override fun onSignInFailed(statusCode: Int) {
        emitSignal(SIGNAL_SIGN_IN_FAILED.name, statusCode)
    }

    override fun onSignOutSuccess() {
        emitSignal(SIGNAL_SIGN_OUT_SUCCESS.name)
    }

    override fun onSignOutFailed() {
        emitSignal(SIGNAL_SIGN_OUT_FAILED.name)
    }

    override fun onPlayerStatsLoaded(statsJson: String) {
        emitSignal(SIGNAL_PLAYER_STATS_LOADED.name, statsJson)
    }

    override fun onPlayerStatsLoadingFailed() {
        emitSignal(SIGNAL_PLAYER_STATS_LOADED_FAILED.name)
    }
}