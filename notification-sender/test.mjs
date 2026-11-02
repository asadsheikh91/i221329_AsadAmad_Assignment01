// Checks deliver() against the Realtime Database emulator with a fake Firebase Cloud Messaging.
//   firebase emulators:start --only database      (in another terminal)
//   FIREBASE_DATABASE_EMULATOR_HOST=127.0.0.1:9000 npm test
import admin from 'firebase-admin';
import { deliver } from './deliver.mjs';

admin.initializeApp({ projectId: 'demo-kinnect', databaseURL: 'http://127.0.0.1:9000?ns=demo-kinnect' });
const db = admin.database();

let failed = 0;
function check(name, condition) {
  console.log(condition ? '  ok   ' : '  FAIL ', name);
  if (!condition) failed++;
}

// A fake FCM that records what it was asked to send; the token "dead" is reported as unregistered
const sent = [];
const fakeMessaging = {
  async sendEachForMulticast(message) {
    sent.push(message);
    const responses = message.tokens.map((token) =>
      token === 'dead' ? { success: false, error: { code: 'messaging/registration-token-not-registered' } } : { success: true });
    return { successCount: responses.filter((r) => r.success).length, responses };
  },
};

await db.ref().set(null);
await db.ref('fcmTokens/bob').set({ good1: true, good2: true, dead: true });

await db.ref('pushQueue/q1').set({ toUid: 'bob', fromUid: 'alice', title: 'Alice Ant', body: 'hello', kind: 'message', createdAt: Date.now() });
const count = await deliver(db, fakeMessaging, 'q1', (await db.ref('pushQueue/q1').get()).val());
check('sends to every token of the receiver', sent[0].tokens.length === 3);
check('counts the phones that got it', count === 2);
check('data message carries sender, title and body', sent[0].data.fromUid === 'alice' && sent[0].data.title === 'Alice Ant' && sent[0].data.body === 'hello');
check('high priority', sent[0].android.priority === 'high');
check('request is removed from the queue', !(await db.ref('pushQueue/q1').get()).exists());
check('dead token is forgotten', !(await db.ref('fcmTokens/bob/dead').get()).exists());
check('good tokens are kept', (await db.ref('fcmTokens/bob/good1').get()).exists());

await db.ref('pushQueue/q2').set({ toUid: 'nobody', fromUid: 'alice', title: 'x', body: 'y', kind: 'message', createdAt: Date.now() });
const none = await deliver(db, fakeMessaging, 'q2', (await db.ref('pushQueue/q2').get()).val());
check('a person without phones gets nothing, request still removed', none === 0 && !(await db.ref('pushQueue/q2').get()).exists());

await db.ref('pushQueue/q3').set({ toUid: 'bob', fromUid: 'alice', title: 'old', body: 'old', kind: 'message', createdAt: Date.now() - 10 * 60 * 1000 });
const before = sent.length;
await deliver(db, fakeMessaging, 'q3', (await db.ref('pushQueue/q3').get()).val());
check('a request older than 5 minutes is not pushed', sent.length === before && !(await db.ref('pushQueue/q3').get()).exists());

await db.ref('pushQueue/q4').set({ toUid: 'bob', fromUid: 'alice', title: 'Alice Ant', body: 'Alice took a screenshot', kind: 'screenshot', createdAt: Date.now() });
await deliver(db, fakeMessaging, 'q4', (await db.ref('pushQueue/q4').get()).val());
check('screenshot alerts are sent too', sent[sent.length - 1].data.kind === 'screenshot');

console.log(failed === 0 ? '\nall passed' : `\n${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
