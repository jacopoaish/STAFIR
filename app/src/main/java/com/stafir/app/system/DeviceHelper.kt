package com.stafir.app.system

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings

/** Aiuti specifici per Xiaomi / HyperOS e per le impostazioni di sistema. */
object DeviceHelper {

    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco") ||
            Build.BRAND.lowercase() in setOf("xiaomi", "redmi", "poco")

    val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    @SuppressLint("BatteryLife")
    fun batteryOptimizationIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    /** Schermata "Avvio automatico" di HyperOS/MIUI (con alternative se cambia nelle versioni). */
    fun autostartIntents(ctx: Context): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),
        appDetailsIntent(ctx),
    )

    /** Impostazioni batteria dell'app su HyperOS ("Nessuna restrizione"). */
    fun xiaomiBatteryIntents(ctx: Context): List<Intent> = listOf(
        Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
            .putExtra("package_name", ctx.packageName)
            .putExtra("package_label", "Stafir"),
        batteryOptimizationIntent(ctx),
        appDetailsIntent(ctx),
    )

    fun appDetailsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** Prova le intent in ordine finché una funziona. */
    fun launchFirst(ctx: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    /** "Gestione contenuti multimediali": cestino e preferiti senza una conferma per ogni file. */
    fun canManageMedia(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(ctx)

    val supportsManageMedia: Boolean get() = Build.VERSION.SDK_INT >= 31

    fun manageMediaIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:${ctx.packageName}"))
        else appDetailsIntent(ctx)
}
