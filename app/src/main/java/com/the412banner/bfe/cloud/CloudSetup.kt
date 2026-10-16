package com.the412banner.bfe.cloud

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives adding (or reconnecting) a remote through rclone's own non-interactive config state machine,
 * so every backend's setup logic stays rclone's: BFE sends the form, then answers each question rclone
 * returns — automatically where the answer is known ([CloudService.autoAnswers], single-choice lists),
 * otherwise by showing it to the user ([Phase.Question]).
 *
 * Browser sign-in: when rclone asks `config_is_local`, BFE answers yes as a background job. rclone
 * then starts its sign-in listener on 127.0.0.1:53682 and the setup job blocks there; BFE reads the
 * sign-in address from `config/oauthstatus`, opens it in the user's browser (Google refuses embedded
 * WebViews), and the job completes when the provider redirects back to that listener. rclone's
 * "done" page is swapped for one with a "Return to BFE" link ([AuthReturnActivity]).
 *
 * Lives outside composition so a sign-in survives the activity being recreated while the user is in
 * the browser. One session at a time.
 */
object CloudSetup {
    private const val TAG = "BFE_CLOUD"

    sealed class Phase {
        data class Working(val message: String) : Phase()
        /** A question for the user; answer with [answer]. */
        data class Question(val option: RcOption, val error: String?) : Phase()
        /** Waiting for the browser sign-in at [url]; [opened] once BFE launched the browser. */
        data class Browser(val url: String, val opened: Boolean) : Phase()
        /** The provider shows a code to paste back ([url] is where to get it). */
        data class Code(val url: String?, val option: RcOption, val error: String?) : Phase()
        /** Saved, but listing failed: keep it, edit, or remove. */
        data class TestFailed(val error: String) : Phase()
        data class Done(val name: String) : Phase()
        data class Failed(val error: String) : Phase()
    }

    data class Session(
        val name: String,
        val type: String,
        val service: CloudService?,
        /** Form values as submitted (kept so "Edit" can reopen the form filled in). */
        val form: Map<String, String>,
        val isNew: Boolean,
        val phase: Phase,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> get() = _session
    private var job: Job? = null
    @Volatile private var pendingAnswer: CompletableDeferred<String>? = null

    /** The one-shot page rclone's sign-in listener shows when the provider sends the user back. */
    private const val AUTH_PAGE =
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>BFE</title><style>body{font-family:sans-serif;margin:0;padding:48px 24px;text-align:center;background:#f6f6f9;color:#1b1b1f}" +
            "@media (prefers-color-scheme:dark){body{background:#131316;color:#e4e1e6}}a{display:inline-block;margin-top:28px;padding:14px 28px;" +
            "border-radius:24px;background:#4f5b92;color:#fff;text-decoration:none;font-weight:600}p{opacity:.8}</style></head><body>" +
            "{{ if .OK }}<h2>Signed in</h2><p>BFE is finishing the connection.</p>" +
            "{{ else }}<h2>Sign-in didn't complete</h2><p>{{ .Name }}</p>{{ if .Description }}<p>{{ .Description }}</p>{{ end }}{{ end }}" +
            "<a href=\"bfe-auth://done\">Return to BFE</a></body></html>"

    private val HINTS = mapOf("config_auth_no_browser" to "true", "config_template" to AUTH_PAGE)

    // ── Starting ─────────────────────────────────────────────────────────

    /**
     * Creates remote [name] of [type] from [form] (curated service fields, or raw rclone options from
     * More services) and runs its setup. [CloudServices.START_PATH_KEY] in [form] is BFE's own.
     */
    fun begin(ctx: Context, name: String, type: String, service: CloudService?, form: Map<String, String>) {
        if (_session.value?.let { it.phase !is Phase.Done && it.phase !is Phase.Failed } == true && job?.isActive == true) return
        val app = ctx.applicationContext
        val params = buildMap {
            service?.fixed?.let { putAll(it) }
            form.forEach { (k, v) -> if (k != CloudServices.START_PATH_KEY && v.isNotEmpty()) put(k, v) }
            putAll(HINTS)
        }
        val startPath = form[CloudServices.START_PATH_KEY]?.trim()?.trim('/').orEmpty()
        _session.value = Session(name, type, service, form, isNew = true, phase = Phase.Working(if (service?.oauth == true) "Preparing sign-in…" else "Connecting…"))
        job = scope.launch {
            try {
                val api = RcloneDaemon.api(app)
                setStartPath(app, name, startPath)
                run(app, api, api.createRemote(name, type, params), startPath)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "setup failed", e)
                fail(friendly(e.message))
            }
        }
    }

    /** Signs an existing remote in again (expired/revoked token, or setup that never finished). */
    fun reconnect(ctx: Context, remote: RcRemote) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        val service = CloudServices.forRemote(remote)
        _session.value = Session(remote.name, remote.type, service, emptyMap(), isNew = false, phase = Phase.Working("Preparing sign-in…"))
        job = scope.launch {
            try {
                val api = RcloneDaemon.api(app)
                run(app, api, api.reconfigure(remote.name, HINTS), startPath(app, remote.name), reconnecting = true)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "reconnect failed", e)
                fail(friendly(e.message))
            }
        }
    }

    // ── User actions ─────────────────────────────────────────────────────

    fun answer(value: String) {
        pendingAnswer?.complete(value)
    }

    fun markBrowserOpened() {
        val s = _session.value ?: return
        val p = s.phase as? Phase.Browser ?: return
        _session.value = s.copy(phase = p.copy(opened = true))
    }

    /** Abandons the flow; a remote that was being added is removed again. */
    fun cancel(ctx: Context) {
        val s = _session.value ?: return
        val app = ctx.applicationContext
        job?.cancel()
        pendingAnswer?.cancel()
        _session.value = null
        scope.launch {
            runCatching {
                val api = RcloneDaemon.api(app)
                api.oauthStop()
                if (s.isNew) { api.deleteRemote(s.name); setStartPath(app, s.name, "") }
            }
        }
    }

    /** "Remove" after a failed connection test. */
    fun discard(ctx: Context) = cancel(ctx)

    /** Closes a finished session (Done / Failed / kept after TestFailed). */
    fun dismiss() {
        if (job?.isActive == true && _session.value?.phase !is Phase.TestFailed) return
        job?.cancel()
        _session.value = null
    }

    // ── The state machine loop ───────────────────────────────────────────

    private suspend fun run(ctx: Context, api: RcloneApi, first: RcConfigStep, startPath: String, reconnecting: Boolean = false) {
        val s0 = _session.value ?: return
        val name = s0.name
        val auto = s0.service?.autoAnswers.orEmpty()
        var step = first
        var guard = 0
        while (!step.isDone) {
            if (++guard > 60) throw IllegalStateException("rclone kept asking questions")
            val opt = step.option
            val error = step.error.ifBlank { null }
            step = when {
                // An error with nothing to answer can't be retried from here.
                opt == null && error != null -> throw IllegalStateException(error)
                opt == null -> api.continueConfig(name, step.state, step.result, HINTS)
                opt.name == "config_is_local" -> browserSignIn(api, name, step)
                // Reconnect = replace the stored sign-in; never ask.
                opt.name == "config_refresh_token" -> api.continueConfig(name, step.state, if (reconnecting) "true" else "false", HINTS)
                opt.name == "config_verification_code" -> {
                    val url = Regex("https?://\\S+").find(opt.help)?.value
                    setPhase(Phase.Code(url, opt, error))
                    val code = awaitAnswer()
                    setPhase(Phase.Working("Signing in…"))
                    api.continueConfig(name, step.state, code, HINTS)
                }
                error == null && auto.containsKey(opt.name) -> api.continueConfig(name, step.state, auto.getValue(opt.name), HINTS)
                error == null && opt.examples.size == 1 && (opt.exclusive || opt.required) ->
                    api.continueConfig(name, step.state, opt.examples[0].value, HINTS)
                else -> {
                    setPhase(Phase.Question(opt, error))
                    val value = awaitAnswer()
                    setPhase(Phase.Working("Saving…"))
                    api.continueConfig(name, step.state, value, HINTS)
                }
            }
        }
        // A closing message (e.g. Drive's "No Shared Drives found") doesn't undo a finished setup;
        // the connection test below decides.
        if (step.error.isNotBlank()) Log.i(TAG, "setup of $name ended with: ${step.error}")

        // Prove it works before calling it done: list the starting folder once.
        setPhase(Phase.Working("Checking the connection…"))
        val tested = runCatching { api.list(name, startPath) }
        if (tested.isFailure) {
            setPhase(Phase.TestFailed(friendly(tested.exceptionOrNull()?.message)))
            return
        }
        setPhase(Phase.Done(name))
    }

    /** Answers "use a browser" as a job, opens the sign-in page, and waits for the provider's redirect. */
    private suspend fun browserSignIn(api: RcloneApi, name: String, step: RcConfigStep): RcConfigStep {
        setPhase(Phase.Working("Opening the sign-in page…"))
        val jobId = api.startContinueConfig(name, step.state, "true", HINTS)
        try {
            // The listener comes up within a second; a job that finishes first needed no browser
            // (or failed, which awaitJob reports below).
            var finishedEarly = false
            val url = withTimeoutOrNull(20_000) {
                var u: String? = null
                while (u == null && !finishedEarly) {
                    u = api.oauthUrl()
                    if (u == null) {
                        finishedEarly = api.rc.jobStatus(jobId).finished
                        if (!finishedEarly) delay(150)
                    }
                }
                u
            }
            if (url != null) setPhase(Phase.Browser(url, opened = false))
            else if (!finishedEarly) {
                api.rc.stopJob(jobId)
                throw IllegalStateException("rclone's sign-in page didn't start. " + RcloneDaemon.logTail().lineSequence().lastOrNull().orEmpty())
            }
            val out = api.rc.awaitJob(jobId, pollMs = 600)
            setPhase(Phase.Working("Finishing sign-in…"))
            return RcConfigStep.parse(out)
        } catch (e: kotlinx.coroutines.CancellationException) {
            api.oauthStop()
            throw e
        }
    }

    private suspend fun awaitAnswer(): String {
        val d = CompletableDeferred<String>()
        pendingAnswer = d
        return try { d.await() } finally { pendingAnswer = null }
    }

    private fun setPhase(p: Phase) {
        _session.value = _session.value?.copy(phase = p)
    }

    private fun fail(msg: String) = setPhase(Phase.Failed(msg))

    /** rclone's errors, shortened to what a person can act on. */
    fun friendly(raw: String?): String {
        val m = raw.orEmpty().trim()
        return when {
            m.isEmpty() -> "Something went wrong."
            m.contains("oauth authentication was cancelled") -> "Sign-in was cancelled."
            m.contains("No code returned by remote server") || m.contains("access_denied") -> "Sign-in was declined in the browser."
            m.contains("failed to start auth webserver") -> "Another sign-in is already waiting. Finish or cancel it, then try again."
            m.contains("no such host") || m.contains("dial tcp") && m.contains("lookup") -> "Couldn't reach the server. Check the address and your connection."
            m.contains("connection refused") -> "The server refused the connection. Check the address and port."
            m.contains("i/o timeout") || m.contains("deadline exceeded") -> "The server didn't answer in time."
            m.contains("401") || m.contains("403") || m.contains("Unauthorized") || m.contains("authentication") || m.contains("Access Denied") || m.contains("AccessDenied") ->
                "The server didn't accept these credentials. " + m.lineSequence().first().take(160)
            else -> m.lineSequence().first().take(240)
        }
    }

    // ── Per-remote starting folder (BFE's own setting) ───────────────────
    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun startPath(ctx: Context, remote: String): String = prefs(ctx).getString("cloud_start_path/$remote", "").orEmpty()

    fun setStartPath(ctx: Context, remote: String, path: String) {
        prefs(ctx).edit().apply { if (path.isEmpty()) remove("cloud_start_path/$remote") else putString("cloud_start_path/$remote", path) }.apply()
    }

    fun renameStartPath(ctx: Context, old: String, new: String) {
        val p = startPath(ctx, old)
        setStartPath(ctx, old, "")
        if (p.isNotEmpty()) setStartPath(ctx, new, p)
    }
}
