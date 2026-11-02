package com.kinnect.i221329_i221069

import com.google.firebase.database.ServerValue

// Call set-up in the Realtime Database. The voice and video themselves travel through Agora (see AgoraCall.kt);
// the database only tells the other phone "somebody is calling" and keeps the history.
//
//   calls/{callId}            callerId, callerName, calleeId, calleeName, video, status, createdAtMs
//                             status goes ringing -> accepted -> ended (or rejected / missed / busy)
//                             The Agora channel has the same name as the call id.
//   callLogs/{uid}/{callId}   peerUid, peerName, video, outgoing, status, time, seconds   (one copy for each person)

fun Backend.newCallId(): String = ref("calls").push().key ?: System.currentTimeMillis().toString()

/** What a call looked like to one person, for the call history. */
data class CallLog(
    val id: String,
    val peerUid: String,
    val peerName: String,
    val video: Boolean,
    val outgoing: Boolean,
    val status: String,
    val time: Long,
    val seconds: Long
)

/** Writes this person's copy of the call history. [status] is connected, missed, declined, busy or no answer. */
fun Backend.writeCallLog(callId: String, peerUid: String, peerName: String, video: Boolean, outgoing: Boolean, status: String, seconds: Long) {
    val uid = myUid ?: return
    val log = hashMapOf<String, Any?>(
        "peerUid" to peerUid,
        "peerName" to peerName,
        "video" to video,
        "outgoing" to outgoing,
        "status" to status,
        "time" to ServerValue.TIMESTAMP,
        "seconds" to seconds
    )
    ref("callLogs/$uid/$callId").setValue(log)
}

/** My call history, newest first. */
fun Backend.listenCallLogs(onChange: (List<CallLog>) -> Unit): ListenerRegistration {
    val uid = myUid!!
    return listen(ref("callLogs/$uid").orderByChild("time").limitToLast(50)) { snap ->
        onChange(snap.children.map {
            CallLog(
                it.key ?: "", it.text("peerUid"), it.text("peerName"), it.flag("video"), it.flag("outgoing"),
                it.text("status"), it.number("time"), it.number("seconds")
            )
        }.sortedByDescending { it.time })
    }
}
