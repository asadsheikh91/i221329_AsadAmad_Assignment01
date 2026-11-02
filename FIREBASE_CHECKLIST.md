# Checklist: from this folder to a running Kinnect

About 20 minutes. Everything is free (Firebase Spark plan, Agora free minutes). Tick each box in order; the app will not
build until step 3 is done.

## 1. Create the Firebase project

- [ ] <https://console.firebase.google.com> > **Create a project** (for example `kinnect`). Analytics can be off.

## 2. Turn on the three services (before you download the config file!)

- [ ] **Build > Authentication > Get started > Sign-in method**: enable **Email/Password** (the first switch) and Save.
- [ ] **Build > Realtime Database > Create database**: choose a location near you, start in **locked mode**.
      Do this *before* step 3: the config file only contains your database address if the database already exists.
- [ ] **Cloud Messaging** needs nothing to switch on (it is part of every project).

## 3. Register the Android app and add `google-services.json`

- [ ] Project overview > **Add app > Android**. Package name: `com.kinnect.i221329_i221069` (it must match exactly; it is the
      `applicationId` in `app/build.gradle.kts`). Register.
- [ ] Download **`google-services.json`** and copy it into the **`app/`** folder (next to `app/build.gradle.kts`).
- [ ] Open the file and check that it contains `"firebase_url": "https://...firebasedatabase.app"` or `...firebaseio.com`.
      If it does not, the database was created after the file was made: download the file again.
- [ ] Never commit this file (it is already in `.gitignore`).

## 4. Publish the security rules

- [ ] Realtime Database > **Rules** tab. Delete what is there, paste the **whole contents of `database.rules.json`**, **Publish**.
      Without the rules every read and write is refused ("permission denied").

## 5. Agora (voice and video calls)

- [ ] <https://console.agora.io> > create an account > **Create project**. Authentication: **Testing mode: App ID**
      (not "Secured mode"). Copy the **App ID**.
- [ ] Create or open the file **`local.properties`** in the project root (next to `settings.gradle.kts`) and add one line:

  ```
  AGORA_APP_ID=paste-your-app-id-here
  ```
  This file is git-ignored, so the App ID is never committed. Without it a call ends at once with "Connection lost".

## 6. Push notifications (FCM)

The phone side is already in the app. Something has to turn "a message was sent" into a push; choose one:

**A. On your laptop (free, recommended for the demo)**

- [ ] Firebase console > Project settings > **Service accounts > Generate new private key**. Save the file as
      `notification-sender/service-account.json` (git-ignored; never commit or share it).
- [ ] In a terminal: `cd notification-sender`, `npm install`, `npm start`. Leave it running while you demo.
      It prints one line for every push it sends.

**B. Cloud Function (nothing runs on your laptop; needs the Blaze plan with a card, usage stays free)**

- [ ] `npm install -g firebase-tools`, `firebase login`, `cd functions && npm install && cd ..`, `firebase deploy --only functions`

## 7. Build and run

- [ ] Open the project in Android Studio, let Gradle sync, run on **two devices** (two phones, or a phone and an emulator
      with Google Play services). Allow notifications, camera and microphone when asked.
- [ ] If the build fails with `AccessDeniedException` inside `app/build`, OneDrive is locking the folder: pause OneDrive sync
      or move the project out of OneDrive, then **Build > Clean Project**.

## 8. First-run test (about 10 minutes, two devices A and B)

On each device **Create new account** (different emails, password of 6 or more characters; watch the strength bar change while typing).

- [ ] After Create account you land on **Set up your profile**. Pick a username. On B try A's username: you get "That username is taken".
- [ ] **Splash** stays 5 seconds, then goes to Home (logged in) or Log in. Close and reopen the app: it goes straight to Home.
- [ ] **A:** Search > type part of B's name or username > **+**. **B:** the bell shows 1 > open it > **Confirm**.
- [ ] **A:** Home > What's on your mind > Photo/video > choose **3 or more photos** (they get numbers 1, 2, 3...) > Next > Post.
      **B** sees the collage with **+N**. Hold **Like** on it and pick a reaction. **A** sees the reaction summary change.
- [ ] **B:** Comment > Reply under a comment > Like a comment > camera icon to add a photo. Watch "Replying to ... Cancel".
- [ ] **A:** Create story > tap the shutter for a photo, or hold it for a short video > Aa for text > the arrow.
      **B** sees it on Home. It disappears after 24 hours.
- [ ] **A and B:** open each other's chat. The Chats list shows a **green dot** and the chat says **Active now**.
      Close B's app: A sees B go offline.
- [ ] **Chat:** send text, a photo (Photos icon) and a post (the + icon). **Long-press a message**: Reply, Heart, Edit, Delete
      (Edit and Delete disappear after 5 minutes; an edited message shows "Edited"). Double-tap a message for a heart.
- [ ] **A:** phone icon for a voice call, camera icon for a video call. **B** rings (app open), accept, talk, hang up.
- [ ] **Push:** put B's app in the background (not swiped away if the sender is not running). **A** sends a message: B gets a notification.
- [ ] **Screenshot:** on A take a screenshot while the chat is open. The chat shows "A took a screenshot" and B is notified.
- [ ] **Profile:** Edit profile > profile picture and cover photo > Save. The picture appears in every avatar.
- [ ] **Log out** (Menu): you are marked offline and Back from Log in leaves the app.

## If something does not work

| What you see | Likely cause |
| --- | --- |
| Build error "google-services.json is missing" | Step 3: wrong folder or file name |
| Everything is empty or "permission denied" in the log | Step 4: rules not published; or step 2 database created after the config file (download it again) |
| Sign-up says "An internal error" / "configuration not found" | Step 2: Email/Password is not enabled |
| A call ends at once, or never connects | Step 5: `AGORA_APP_ID` missing in `local.properties` (rebuild), or the Agora project is in secured mode |
| No push notification | Step 6: the sender is not running; or notifications are not allowed for Kinnect in the phone settings |
| Two people cannot see each other's posts | They are not friends yet: the feed shows only you and your friends |
| A device never rings | Calls ring only while the app is open on that device |

More detail on how everything fits together: `BACKEND_SETUP.md`. To study for the demo and the exam: `EXAM_GUIDE.md`.
