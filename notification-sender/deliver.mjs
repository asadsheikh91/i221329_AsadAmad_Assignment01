// The one function that does the work: turn a request from pushQueue/ into a push notification.
// It is used by sender.mjs (run on your laptop) and has the same logic as functions/index.js (Cloud Function).
//
// The app writes a request like this when somebody sends a message or takes a screenshot:
//   pushQueue/{id} = { toUid, fromUid, title, body, kind: "message" | "screenshot", createdAt }
// The phone of the receiver registered its token under fcmTokens/{toUid}/{token}.

// Requests older than this are not pushed any more (the sender was off for a while)
const MAX_AGE_MS = 5 * 60 * 1000;

// FCM answers with these codes for a token that no longer belongs to any phone
const DEAD_TOKEN_CODES = ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'];

/**
 * @param db         a firebase-admin Realtime Database
 * @param messaging  a firebase-admin Messaging (or anything with sendEachForMulticast)
 * @param id         the key of the request in pushQueue/
 * @param item       the request itself
 * @returns how many phones were asked to show the notification
 */
export async function deliver(db, messaging, id, item) {
  const queueEntry = db.ref(`pushQueue/${id}`);
  try {
    if (!item || !item.toUid || !item.fromUid) {
      return 0;
    }
    if (item.createdAt && Date.now() - item.createdAt > MAX_AGE_MS) {
      return 0;
    }

    const snapshot = await db.ref(`fcmTokens/${item.toUid}`).get();
    const tokens = Object.keys(snapshot.val() || {});
    if (tokens.length === 0) {
      return 0;
    }

    // A "data" message (not a "notification" message) so the app's own code always runs and can open the right chat
    const response = await messaging.sendEachForMulticast({
      tokens,
      data: {
        fromUid: String(item.fromUid),
        title: String(item.title || 'Kinnect'),
        body: String(item.body || ''),
        kind: String(item.kind || 'message'),
      },
      android: { priority: 'high' },
    });

    // Forget tokens of phones that uninstalled the app
    const removals = [];
    response.responses.forEach((result, index) => {
      if (!result.success && result.error && DEAD_TOKEN_CODES.includes(result.error.code)) {
        removals.push(db.ref(`fcmTokens/${item.toUid}/${tokens[index]}`).remove());
      }
    });
    await Promise.all(removals);
    return response.successCount;
  } finally {
    // Whatever happened, the request is done
    await queueEntry.remove();
  }
}
