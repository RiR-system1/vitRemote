package com.example.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.RemoteService
import com.example.data.InstalledAppEntry

object DeviceActionExecutor {

    fun queryLocalInstalledApps(context: Context): List<InstalledAppEntry> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        val byPkg = LinkedHashMap<String, InstalledAppEntry>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || byPkg.containsKey(pkg)) continue
            val label = ri.loadLabel(pm)?.toString()?.trim()?.ifBlank { pkg } ?: pkg
            val flags = ri.activityInfo.applicationInfo.flags
            val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            byPkg[pkg] = InstalledAppEntry(label = label, packageName = pkg, systemApp = isSystem)
        }
        return byPkg.values.sortedWith(
            compareBy<InstalledAppEntry> { it.systemApp }.thenBy { it.label.lowercase() },
        )
    }

    fun executeOwnDeviceAction(context: Context, plan: AiScreenPlan): String {
        val svc = RemoteService.instance
        return when (plan.action.lowercase()) {
            "open_app" -> {
                val pkg = plan.packageName.trim()
                val launch = if (pkg.isNotEmpty()) context.packageManager.getLaunchIntentForPackage(pkg) else null
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launch)
                    plan.reply.ifBlank { "অ্যাপটি ওপেন করা হচ্ছে" }
                } else {
                    "এই অ্যাপটি (${pkg}) ফোনে খুঁজে পাওয়া যায়নি"
                }
            }
            "call_contact" -> {
                val query = plan.phoneQuery.trim()
                val number = resolvePhoneNumber(context, query)
                if (!number.isNullOrBlank()) {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    plan.reply.ifBlank { "$query ($number) নম্বরে কল করার জন্য ডায়ালার খোলা হয়েছে" }
                } else {
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                    "কন্টাক্টে '$query' খুঁজে পাওয়া যায়নি, ডায়ালার খোলা হয়েছে"
                }
            }
            "click_text" -> {
                if (svc == null) return "নিজের স্ক্রিনে ক্লিক করতে Accessibility চালু করুন"
                val clicked = svc.performLocalClickByText(plan.targetText)
                if (!clicked) {
                    svc.performLocalTap(plan.xNorm, plan.yNorm)
                }
                plan.reply
            }
            "click_xy" -> {
                if (svc == null) return "স্ক্রিনে টাচ করতে Accessibility চালু করুন"
                svc.performLocalTap(plan.xNorm, plan.yNorm)
                plan.reply
            }
            "swipe" -> {
                if (svc == null) return "সোয়াইপ করতে Accessibility চালু করুন"
                svc.performLocalSwipe(plan.xNorm, plan.yNorm, plan.x2Norm, plan.y2Norm)
                plan.reply
            }
            "scroll" -> {
                if (svc == null) return "স্ক্রল করতে Accessibility চালু করুন"
                val dir = plan.targetText.lowercase().takeIf { it in listOf("up", "down", "left", "right") } ?: "down"
                svc.performLocalScroll(dir)
                plan.reply
            }
            "type_text" -> {
                if (svc == null) return "লিখতে Accessibility চালু করুন"
                svc.performLocalType(plan.inputText)
                plan.reply
            }
            "back" -> {
                svc?.performLocalGlobal("back")
                plan.reply
            }
            "home" -> {
                svc?.performLocalGlobal("home")
                plan.reply
            }
            "recents" -> {
                svc?.performLocalGlobal("recents")
                plan.reply
            }
            "notifications" -> {
                svc?.performLocalGlobal("notif")
                plan.reply
            }
            "torch" -> {
                val state = when (plan.targetText.lowercase()) {
                    "on" -> true
                    "off" -> false
                    else -> null
                }
                svc?.performLocalTorch(state)
                plan.reply
            }
            else -> plan.reply.ifBlank { "ঠিক আছে" }
        }
    }

    fun executeRemoteOrVirtualAction(
        plan: AiScreenPlan,
        isVirtual: Boolean,
        sendCommand: (String) -> Unit,
        launchRealApp: (String) -> Unit,
        launchVirtualApp: (String) -> Unit,
    ): String {
        val gPrefix = if (isVirtual) "V" else ""
        when (plan.action.lowercase()) {
            "open_app" -> {
                val pkg = plan.packageName.trim()
                if (pkg.isNotEmpty()) {
                    if (isVirtual) launchVirtualApp(pkg) else launchRealApp(pkg)
                }
            }
            "click_text", "click_xy" -> {
                sendCommand("${gPrefix}t:${plan.xNorm},${plan.yNorm}")
            }
            "swipe" -> {
                sendCommand("${gPrefix}s:${plan.xNorm},${plan.yNorm},${plan.x2Norm},${plan.y2Norm},260")
            }
            "scroll" -> {
                val dir = plan.targetText.lowercase().takeIf { it in listOf("up", "down", "left", "right") } ?: "down"
                sendCommand("${gPrefix}g:$dir")
            }
            "type_text" -> {
                if (plan.inputText.isNotEmpty()) {
                    sendCommand("x:${plan.inputText}")
                }
            }
            "back" -> sendCommand(if (isVirtual) "Va:back" else "a:back")
            "home" -> if (!isVirtual) sendCommand("a:home")
            "recents" -> if (!isVirtual) sendCommand("a:recents")
            "notifications" -> if (!isVirtual) sendCommand("a:notif")
            "torch" -> {
                val target = when (plan.targetText.lowercase()) {
                    "on" -> "on"
                    "off" -> "off"
                    else -> "toggle"
                }
                sendCommand("f:$target")
            }
        }
        return plan.reply.ifBlank { "কমান্ড পাঠানো হয়েছে" }
    }

    private fun resolvePhoneNumber(context: Context, query: String): String? {
        if (query.isBlank()) return null
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5) {
            return digits
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val qLower = query.trim().lowercase()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)?.trim().orEmpty()
                    val num = cursor.getString(1)?.trim().orEmpty()
                    if (name.lowercase().contains(qLower) && num.isNotBlank()) {
                        return num
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
