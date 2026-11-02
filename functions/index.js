// The same job as notification-sender/, as a Cloud Function: it runs on Google's servers, so nothing has to be
// running on your laptop. Deploying functions needs the Blaze (pay as you go) plan of Firebase; the free amount
// is far more than a class project uses.
//
//   npm install -g firebase-tools     then      firebase login
//   cd functions && npm install && cd ..
//   firebase deploy --only functions
const { onValueCreated } = require('firebase-functions/v2/database');
const admin = require('firebase-admin');

admin.initializeApp();

const MAX_AGE_MS = 5 * 60 * 1000;
const DEAD_TOKEN_CODES = ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'];

// Runs for every new request the app writes into pushQueue/{id}
exports.sendPush = onValueCreated('/pushQueue/{id}', async (event) => {
  const db = admin.database();
  const item = event.data.val();
  try {
    if (!item || !item.toUid || !item.fromUid) return;
    if (item.createdAt && Date.now() - item.createdAt > MAX_AGE_MS) return;

    const snapshot = await db.ref(`fcmTokens/${item.toUid}`).get();
    const tokens = Object.keys(snapshot.val() || {});
    if (tokens.length === 0) return;

    const response = await admin.messaging().sendEachForMulticast({
      tokens,
      data: {
        fromUid: String(item.fromUid),
        title: String(item.title || 'Kinnect'),
        body: String(item.body || ''),
        kind: String(item.kind || 'message'),
      },
      android: { priority: 'high' },
    });

    const removals = [];
    response.responses.forEach((result, index) => {
      if (!result.success && result.error && DEAD_TOKEN_CODES.includes(result.error.code)) {
        removals.push(db.ref(`fcmTokens/${item.toUid}/${tokens[index]}`).remove());
      }
    });
    await Promise.all(removals);
  } finally {
    await event.data.ref.remove();
  }
});
