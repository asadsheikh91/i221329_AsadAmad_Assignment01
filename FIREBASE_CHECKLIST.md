# Firebase checklist: from this folder to a running Kinnect

About 10 minutes. The free Spark plan is enough (no card, no Firebase Storage, no Cloud Functions).
Tick each box in order. The app will not build until step 3 is done.

## 1. Create the Firebase project

- [ ] Open <https://console.firebase.google.com> and sign in with a Google account.
- [ ] **Create a project** (any name, for example `kinnect`). Google Analytics can be switched off.

## 2. Register the Android app

- [ ] In the project, click the **Android** icon (**Add app**).
- [ ] **Android package name:** `com.asadamad.i221329` (it must match exactly).
- [ ] App nickname: anything. SHA-1: leave empty (not needed for email and password sign-in).
- [ ] Click **Register app**.

## 3. Put `google-services.json` in the project

- [ ] Download **`google-services.json`** from that step.
- [ ] Copy it into the **`app/`** folder, next to `app/build.gradle.kts`:

  ```
  i221329_AsadAmad_Assignment01-main/
    app/
      build.gradle.kts
      google-services.json     <-- here
      src/
  ```
- [ ] Skip the console's "add the SDK" instructions. The Gradle files are already set up.
- [ ] Do not commit this file to a public repository (it holds your project's keys).

## 4. Turn on sign-in

- [ ] **Build > Authentication > Get started > Sign-in method**.
- [ ] Enable **Email/Password** (only the first switch) and **Save**.

## 5. Create the database

- [ ] **Build > Firestore Database > Create database**.
- [ ] Choose a location close to you (it cannot be changed later).
- [ ] Choose **production mode**. The rules in the next step replace the default ones.

## 6. Publish the security rules

- [ ] In Firestore open the **Rules** tab.
- [ ] Delete what is there, paste the **whole contents of `firestore.rules`** (project root) and click **Publish**.
- [ ] You should see no red error markers. These rules cover profiles, friends, chats, posts and reactions,
      stories, notifications, marketplace listings and calls. Without them, features fail with
      "permission denied" and nothing is saved.

## 7. Build and run

- [ ] Open the project in Android Studio and let Gradle sync.
- [ ] Run on **two devices** (two phones, or one phone and an emulator), because friends, chat, calls and
      notifications need two accounts.
- [ ] If the build fails with `AccessDeniedException` inside `app/build`, OneDrive is locking the folder.
      Pause OneDrive sync or move the project out of OneDrive, then **Build > Clean Project**.

## 8. First-run test (about 5 minutes)

On each device choose **Create new account**, with a different email each (password of 6 or more characters).
Call the devices **A** and **B**.

- [ ] **A:** search for B's name, tap **+**. **B:** the bell shows **1**; open it and tap **Confirm**.
- [ ] **A:** the bell shows **1** ("B accepted your friend request").
- [ ] **A:** Home > **What's on your mind** > write something, add a photo > **Post**. **B** sees it in the feed.
- [ ] **B:** like it, then hold **Like** and choose **Love**. **A:** a notification arrives.
- [ ] **B:** comment on it. **A:** a notification arrives.
- [ ] **A:** Home > **Create story** > allow the camera > shutter (or the gallery thumbnail) > **Aa** to add text >
      the arrow. **B:** the story appears on the Home row; open it and tap **Love** or type a reply.
- [ ] **A:** open **Your story**: "Seen by 1" and the chat shows B's reply.
- [ ] **A:** Marketplace > **Sell** > fill in the form > **List it**. **B** sees it and can **Message seller**.
- [ ] **A:** open a chat with B and tap the phone icon. **B** rings (the app must be open); tap the green button.

## If something does not work

| What you see | Likely cause |
| --- | --- |
| Build error "google-services.json is missing" | Step 3: wrong folder, or wrong file name |
| Sign-up says "An internal error" or "configuration not found" | Step 4: Email/Password is not enabled |
| Posts, stories or notifications do not appear or save | Step 6: rules not published (or pasted partly) |
| Sign-up works but the profile is missing | Step 5 or 6: database not created, or rules not published |
| Calls connect but there is no sound or picture | Strict mobile network: add a TURN server (see `BACKEND_SETUP.md`) |
| A device never rings | Calls ring only while the app is open on that device |

More detail on how everything fits together is in `BACKEND_SETUP.md`.
