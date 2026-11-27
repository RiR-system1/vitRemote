package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleDevice(uid: String, code: String) = DeviceNode(
        ownerUid = uid,
        deviceCode = code,
        model = "Pixel 9 Pro",
        deviceAlias = "অফিস ফোন",
        androidVersion = "15",
        sdk = 35L,
        online = true,
        cloudEnabled = true,
        videoStreaming = false,
        screenWidth = 1080L,
        screenHeight = 2400L,
        batteryLevel = 88L,
        batteryTempTenths = 330L,
        charging = true,
        ramUsedMb = 4100L,
        ramTotalMb = 12288L,
        storageUsedMb = 45000L,
        storageTotalMb = 128000L,
        brightness = 140L,
        torchOn = false,
        ringing = false,
        clipboardText = "",
        captureMode = "Cloud Stream Ready",
        viewerActive = false,
        viewerQuality = 480L,
    )

    @Test
    fun ensureDeviceBinding_enforcesOneAccountPerDevice_andPersistsCode() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val deviceId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val permanentCode = ((System.nanoTime() % 899_999_999L) + 100_000_000L).toString()
        val repo = CloudRepository(firestore)

        val firstRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureDeviceBinding(aliceUid, ALICE_EMAIL, deviceId, permanentCode, "Pixel 9 Pro", "15")
        }
        assertTrue(firstRes.isSuccess)
        assertEquals(BindingCheckResult.Bound(permanentCode), firstRes.getOrThrow())

        val repeatRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureDeviceBinding(aliceUid, ALICE_EMAIL, deviceId, "111222333", "Pixel 9 Pro", "15")
        }
        assertEquals(BindingCheckResult.Bound(permanentCode), repeatRes.getOrThrow())

        val bobUid = signInTestUser(BOB_EMAIL)
        val hijackRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureDeviceBinding(bobUid, BOB_EMAIL, deviceId, "999888777", "Pixel 9 Pro", "15")
        }
        assertTrue(hijackRes.isSuccess)
        assertTrue(hijackRes.getOrThrow() is BindingCheckResult.LockedToOtherAccount)
    }

    @Test
    fun connectByCode_crossUserSession_chat_andCalls_succeed() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceDevId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val bobDevId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val code = ((System.nanoTime() % 899_999_999L) + 100_000_000L).toString()
        val repo = CloudRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureDeviceBinding(aliceUid, ALICE_EMAIL, aliceDevId, code, "Pixel 9 Pro", "15").getOrThrow()
            repo.upsertDevice(aliceUid, aliceDevId, sampleDevice(aliceUid, code)).getOrThrow()
            repo.publishFrame(
                aliceUid,
                aliceDevId,
                LiveFrame(
                    ownerUid = aliceUid,
                    seq = 1L,
                    width = 480L,
                    height = 1066L,
                    isFullShot = false,
                    imageBase64 = "ZmFrZV9mcmFtZQ==",
                ),
            ).getOrThrow()
        }

        // Bob connects using Alice's 9-digit code, saves alias/pin, opens chat, sends message & photo
        val bobUid = signInTestUser(BOB_EMAIL)
        val connectedDevice = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.connectByCode(code, bobUid).getOrThrow()
        }
        assertEquals(aliceDevId, connectedDevice.id)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateSavedDeviceMeta(bobUid, aliceDevId, alias = "বসের ফোন", pinned = true).getOrThrow()
            repo.setViewerState(
                targetDeviceId = aliceDevId,
                viewerActive = true,
                viewerQuality = 480L,
                viewerUid = bobUid,
                sessionCode = code,
            ).getOrThrow()
        }

        val chat = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureChatThread(
                localUid = bobUid,
                localDeviceId = bobDevId,
                localDeviceName = "Galaxy S24",
                targetDevice = connectedDevice.copy(id = aliceDevId),
                authCode = code,
            ).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendTextMessage(chat.id, bobUid, bobDevId, "Galaxy S24", "হ্যালো!").getOrThrow()
            repo.sendPhotoMessage(chat.id, bobUid, bobDevId, "Galaxy S24", "ZmFrZV9waG90bw==", "স্ক্রিনশট").getOrThrow()
        }

        val call = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.startCall(chat.id, bobUid, bobDevId, "Galaxy S24", aliceUid, "video").getOrThrow()
        }

        // Alice sees chat in her inbox, reads messages, accepts call, publishes audio/video stream
        signInTestUser(ALICE_EMAIL)
        val aliceChats = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChats(aliceUid).first { it.any { c -> c.id == chat.id } }
        }
        assertEquals(call.id, aliceChats.first { it.id == chat.id }.activeCallId)

        val msgs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(chat.id).first { it.size >= 3 }
        }
        assertTrue(msgs.any { it.type == "photo" && it.imageBase64 == "ZmFrZV9waG90bw==" })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateCallStatus(chat.id, call.id, "active").getOrThrow()
            repo.publishCallStream(chat.id, call.id, aliceUid, 1L, "AAAA", "BBBB", muted = false, cameraOn = true).getOrThrow()
            repo.updateCallStatus(chat.id, call.id, "ended").getOrThrow()
            repo.publishInstalledApps(
                ownerUid = aliceUid,
                deviceId = aliceDevId,
                apps = listOf(
                    InstalledAppEntry(label = "WhatsApp", packageName = "com.whatsapp", systemApp = false),
                    InstalledAppEntry(label = "YouTube", packageName = "com.google.android.youtube", systemApp = true),
                ),
            ).getOrThrow()
            repo.publishNotifications(
                ownerUid = aliceUid,
                deviceId = aliceDevId,
                listenerEnabled = true,
                notifications = listOf(
                    RemoteNotificationEntry(
                        key = "k_1",
                        packageName = "com.whatsapp",
                        appLabel = "WhatsApp",
                        title = "Team Lead",
                        text = "Meeting in 5 min",
                        postedAtMs = 1700000000000L,
                        ongoing = false,
                    ),
                ),
            ).getOrThrow()
            repo.publishVirtualFrame(
                aliceUid,
                aliceDevId,
                LiveFrame(
                    ownerUid = aliceUid,
                    seq = 3L,
                    width = 480L,
                    height = 960L,
                    isFullShot = false,
                    imageBase64 = "dmlydHVhbF9mcmFtZQ==",
                ),
            ).getOrThrow()
        }

        // Bob reads installed apps, notifications, virtual screen frames, and triggers commands
        signInTestUser(BOB_EMAIL)
        val apps = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeInstalledApps(aliceDevId).first { it.size == 2 }
        }
        assertEquals("WhatsApp", apps.first().label)
        val notifs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeNotifications(aliceDevId).first { it != null && it.notifications.isNotEmpty() }
        }
        assertEquals("Team Lead", notifs!!.notifications.first().title)
        val vFrame = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeVirtualFrame(aliceDevId).first { it != null && it.seq == 3L }
        }
        assertEquals("dmlydHVhbF9mcmFtZQ==", vFrame!!.imageBase64)

        val launchCmd = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendCommand(aliceDevId, bobUid, bobDevId, code, "A:vopen:com.whatsapp")
        }
        assertTrue(launchCmd.isSuccess)
    }

    @Test
    fun strangerWithoutCode_deniedFrameReadAndCommands() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceDevId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val code = ((System.nanoTime() % 899_999_999L) + 100_000_000L).toString()
        val repo = CloudRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureDeviceBinding(aliceUid, ALICE_EMAIL, aliceDevId, code, "Pixel 9 Pro", "15").getOrThrow()
            repo.upsertDevice(aliceUid, aliceDevId, sampleDevice(aliceUid, code)).getOrThrow()
            repo.publishFrame(
                aliceUid,
                aliceDevId,
                LiveFrame(
                    ownerUid = aliceUid,
                    seq = 1L,
                    width = 480L,
                    height = 1066L,
                    isFullShot = false,
                    imageBase64 = "ZmFrZV9mcmFtZQ==",
                ),
            ).getOrThrow()
        }

        val bobUid = signInTestUser(BOB_EMAIL)
        val wrongViewer = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.setViewerState(aliceDevId, true, 480L, bobUid, "000000000")
        }
        assertTrue(wrongViewer.isFailure)

        val frameErr = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeLatestFrame(aliceDevId).first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull(frameErr)
        val fsEx = checkNotNull(frameErr!!.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $frameErr"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    @Test
    fun unauthenticatedAccess_deniedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudRepository(firestore)

        val error = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeOwnDevices("some_uid").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull(error)
        val fsEx = checkNotNull(error!!.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException, got $error"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_vit@test.com"
        const val BOB_EMAIL = "bob_vit@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
