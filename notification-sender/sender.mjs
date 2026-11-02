// Run this on your laptop while you demo the app:
//
//   1. Firebase console > Project settings > Service accounts > Generate new private key.
//      Save the file as notification-sender/service-account.json (it is git-ignored: never commit it).
//   2. In this folder:   npm install      then      npm start
//
// It watches pushQueue/ in the Realtime Database. For every new message the app writes there, it sends a push
// notification (Firebase Cloud Messaging) to every phone of the receiver, even if the Kinnect app is closed.
import { readFileSync } from 'node:fs';
import admin from 'firebase-admin';
import { deliver } from './deliver.mjs';

const keyFile = process.env.GOOGLE_APPLICATION_CREDENTIALS || new URL('./service-account.json', import.meta.url);
const serviceAccount = JSON.parse(readFileSync(keyFile, 'utf8'));
const databaseURL = process.env.DATABASE_URL || `https://${serviceAccount.project_id}-default-rtdb.firebaseio.com`;

admin.initializeApp({ credential: admin.credential.cert(serviceAccount), databaseURL });
const db = admin.database();
const messaging = admin.messaging();

console.log('Kinnect notification sender is running. Waiting for messages... (Ctrl+C to stop)');
db.ref('pushQueue').on('child_added', async (snapshot) => {
  try {
    const sent = await deliver(db, messaging, snapshot.key, snapshot.val());
    console.log(`push ${snapshot.key}: ${sent} phone(s)`);
  } catch (error) {
    console.error(`push ${snapshot.key} failed:`, error.message);
  }
});
