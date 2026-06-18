package com.bosketsalimentos.app

import android.content.Context

/**
 * Where the app finds the Bosket's Alimentos website.
 * The default comes from BuildConfig.SITE_URL; the user can override it at
 * runtime (first launch, or via "Change server address" on the offline screen),
 * so moving servers never requires rebuilding the APK.
 */
object ServerConfig {

    private const val PREFS = "boskets_config"
    private const val KEY_URL = "site_url"
    private const val PLACEHOLDER_MARKER = "YOURDOMAIN"

    fun url(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, null)
        return (saved ?: BuildConfig.SITE_URL).trimEnd('/')
    }

    fun isConfigured(context: Context): Boolean =
        !url(context).contains(PLACEHOLDER_MARKER)

    fun save(context: Context, rawUrl: String): Boolean {
        var u = rawUrl.trim().trimEnd('/')
        if (u.isEmpty()) return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            // Local/private addresses (testing against a dev PC) almost never
            // have TLS — default those to http, real domains to https.
            u = if (isPrivateHost(u)) "http://$u" else "https://$u"
        }
        if (!android.util.Patterns.WEB_URL.matcher(u).matches()) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, u).apply()
        return true
    }

    private fun isPrivateHost(input: String): Boolean {
        val host = input.substringBefore('/').substringBefore(':')
        if (host.equals("localhost", true)) return true
        val parts = host.split('.')
        if (parts.size != 4 || parts.any { it.toIntOrNull() == null }) return false
        val (a, b) = parts.map { it.toInt() }
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31)
    }
}
