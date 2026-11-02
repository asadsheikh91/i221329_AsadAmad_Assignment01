# Study guide for the demo and the exam

Both members must be able to explain any part and change it live. Read this once, then open each file named below and read it
slowly; the code is written in plain steps with comments on purpose.

## 1. Every requirement of the assignment, and where it is

| Assignment part | What the app does | Where to look |
| --- | --- | --- |
| **Splash** (5 s, then Home or Log in) | 5 second timer; logged in goes to Home, otherwise Log in | `MainActivity.kt` (`SPLASH_MS`) |
| **Sign up / log in / log out** with Firebase Auth | Email and password. Sign up saves the user in the database | `SignUpActivity.kt`, `LoginActivity.kt`, `Backend.signUp / logIn / logOut` |
| Password strength while typing | Three bars and a word (Weak, Medium, Good, Strong) change with every key | `ViewTools.kt` `wirePasswordStrength`, `Models.kt` `passwordStrength` |
| **First-time users -> profile setup** (unique username, bio) | After sign up (and after log in if never finished) Edit profile opens in setup mode; Back is blocked until a username is saved | `EditProfileActivity.kt`, `Backend.saveProfile`, `database.rules.json` (`usernames`) |
| Recent login card | Last account on this phone; tapping fills in the email | `LoginActivity.showRecentLogin` |
| Log out marks offline, clears back stack | Writes `presence = offline`, signs out, `CLEAR_TASK` | `Backend.logOut`, `Navigation.logOut` |
| **Stories**: photo or video, text label, Your story / Close friends | Camera (tap = photo, hold = 10 s video) or gallery; editor text, emoji, effects, tags | `StoryCameraActivity.kt`, `PhotoPickerActivity.kt`, `StoryEditorActivity.kt`, `Stories.kt` |
| Stories vanish after 24 h | `expiresAt` = now + 24 h; the listener only asks for stories with `expiresAt` in the future; your own expired ones are deleted | `Stories.kt` (`STORY_LIFETIME_MS`, `listenStories`, `deleteExpiredStories`) |
| Friends' stories at the top of Home, Create story first | Horizontal row built from the stories of you and your friends | `HomeActivity.showStories` |
| **Posts** with one or more images, collage with "+N" | Photo picker with numbered multi-select; feed collage: 1 big, 2 side by side, 3+ with "+N" on the last | `CreatePostActivity.kt`, `PhotoPickerActivity.kt`, `PostUi.showPostPictures`, `item_post.xml` |
| **Six reactions**, one per person, change or remove, live summary | `posts/{id}/reactions/{uid}`: one key per person, so one reaction each. Tap Like = toggle, hold Like = picker. Circles and counts follow the data | `PostsBackend.kt`, `PostDetailActivity.kt`, `PostUi.kt` |
| **Comments** with replies, likes, image comments, "Replying to / Cancel" | Replies point to a parent comment (`parentId`); thread drawn with the branch icon | `CommentsActivity.kt`, `PostsBackend.addComment / toggleCommentLike` |
| Feed = you and your friends, newest first | All newest posts arrive, Home filters by author | `HomeActivity.showPosts` |
| **Chats** list: last message, time, unread | `userChats/{uid}` with `unread` counter; bold + teal dot | `ChatsActivity.kt`, `ChatBackend.listenChats` |
| Send text, images, posts | `MSG_TEXT / MSG_IMAGE / MSG_POST` | `ChatBackend.kt`, `ChatActivity.kt` |
| **Edit / delete within 5 minutes**, "Edited" | `Message.canChange()`; the rules also check `now < createdAt + 300000` | `Models.kt` (`EDIT_WINDOW_MS`), `ChatBackend.editMessage / deleteMessage`, `database.rules.json` |
| Replies and heart on messages | Long-press > Reply; double-tap = heart; both saved on the message | `ChatActivity.kt` |
| **Voice and video calls with Agora** | Phone and camera icons start a call | `AgoraCall.kt`, `BaseCallActivity.kt`, `CallBackend.kt` |
| **Friend system** | Send, confirm, delete from Search, Other profile, Friends, Notifications. Button goes Add friend > Requested > Friends | `Backend.sendFriendRequest / acceptFriendRequest / removeFriendship`, `listenFriendships` |
| Real pending count, friends list, friend count, mutual friends | Counts come from `requests/`, `friends/`; mutual = my friends that are also their friends | `FriendsActivity.kt`, `ProfileActivity.kt`, `UserProfileActivity.kt` |
| Profile picture, cover, name, username, bio | Edit profile; pictures are small Base64 texts | `EditProfileActivity.kt`, `Avatars.kt` |
| **Push notifications (FCM)** for new messages | Message writes `pushQueue`; the sender asks FCM; the service shows it | `ChatBackend.pushMessage`, `notification-sender/`, `functions/`, `KinnectMessagingService.kt` |
| **Search** by username or name while typing | Three indexed prefix queries on `usernameLower`, `nameLower`, `lastNameLower` | `SearchActivity.kt`, `Backend.searchUsers`, `.indexOn` in the rules |
| **Online / offline**: green dot in Chats, "Active now" in Chat | `presence/{uid}` plus `onDisconnect` | `Backend.startPresence`, `KinnectApp.kt`, `ChatsActivity.kt`, `ChatActivity.showPresence` |
| **Screenshot** notification and system message | Android 14 callback, or a gallery watcher on older phones; writes "Aisha took a screenshot" | `ScreenshotWatcher.kt`, `ChatBackend.reportScreenshot` |
| **Two Espresso tests** | Log in and read the profile; create a post and see it in the feed | `app/src/androidTest/.../BackendFlowTest.kt` |

## 2. Questions you should be able to answer

1. **Why Realtime Database?** The assignment asks for it. It is one JSON tree; listeners push changes to the phone live. Data is read with `ValueEventListener`, written with `setValue` / `updateChildren`.
2. **What is `updateChildren` with paths like `"posts/x/text"`?** One atomic write to many places. Either all of it happens or none. A chat message and both chat-list entries are written like this (`ChatBackend.pushMessage`).
3. **How is a username unique?** `usernames/{name} = uid`. The rules allow writing a name only if nobody has it (`!data.exists()`) or you own it. Dots are not allowed because a dot cannot be in a database key.
4. **Why is "one reaction per user" automatic?** The reaction is stored under the user's own id, `reactions/{uid}`. A second tap overwrites the first or removes the key.
5. **How does online/offline work when the app crashes?** `onDisconnect().setValue(offline)` is registered on the server, which runs it when the connection drops.
6. **What does the Realtime Database rule file do?** It is the only security: who may read or write each path. Example: `messages/$chatId` can only be read if the chat id contains your uid.
7. **Authentication vs rules?** Auth proves who you are (`auth.uid`); the rules decide what that person may touch.
8. **How does a push reach a closed app?** Phone registers an FCM token under `fcmTokens/{uid}`. Sender (laptop script or Cloud Function) reads a request from `pushQueue`, sends a *data message* to those tokens through FCM; `KinnectMessagingService.onMessageReceived` builds the notification. The app's own server key never sits in the app.
9. **Why a data message and not a notification message?** A notification message is shown by the system and our code would not run, so we could not open the right chat when tapped.
10. **How does a call connect?** Signalling (who calls whom) goes through `calls/{id}` in the database. Both phones then join the same Agora channel (named like the call id); Agora connects the audio/video. We never touch the media.
11. **Why are pictures Base64 text?** Firebase Storage needs a paid plan. Pictures are shrunk first (`ImageTools`) so they fit; the feed loads text first and pictures one by one.
12. **How does the 5 minute edit rule work?** The app hides Edit and Delete once `now - createdAt >= 5 min`, and the database rule refuses the write too, so a modified app could not cheat.
13. **How are stories removed after 24 hours?** They are filtered by `expiresAt` when listed, and the author's phone deletes its own expired ones.
14. **How do you detect a screenshot?** Android 14+: `registerScreenCaptureCallback`. Older: a `ContentObserver` on the picture library looking for a new file with "screenshot" in its name.
15. **What is the `AGORA_APP_ID` for and why `local.properties`?** It identifies the Agora project. `local.properties` is not committed, so the secret stays off GitHub; Gradle copies it into `BuildConfig.AGORA_APP_ID`.

## 3. Live changes an examiner may ask for, and how to do them

| Change | Do this |
| --- | --- |
| Edit window 5 -> 10 minutes | `EDIT_WINDOW_MS` in `Models.kt` to `10 * 60 * 1000L`, and `300000` -> `600000` in `database.rules.json` (re-publish the rules) |
| Stories last 1 minute (to show expiry) | `STORY_LIFETIME_MS` in `Stories.kt` = `60_000L` |
| Splash 3 seconds | `SPLASH_MS` in `MainActivity.kt` |
| Collage shows "+N" from the 5th photo | `showPostPictures` in `PostUi.kt` (the `count > 3` and `count - 3` numbers) |
| Max 5 photos per post | `maxPhotos` in `CreatePostActivity.kt` and `MAX_PHOTOS` in `PhotoPickerActivity.kt` |
| Stricter password strength | `passwordStrength` in `Models.kt` |
| Username up to 15 letters | `isValidUsername` in `Models.kt` and the `matches(...)` in the `usernames` rule |
| Search also by email | add the field in `Backend.searchUsers` + `.indexOn` + a lower-case copy written in `signUp` and `saveProfile` |
| Call rings 20 seconds only | `RING_TIMEOUT_MS` in `BaseCallActivity.kt` |
| Change the push text | `title` / `body` in `ChatBackend.pushMessage` |
| "Active now" wording | `showPresence` in `ChatActivity.kt` |
| New reaction name | `REACTIONS` in `PostsBackend.kt`, the `validate` list in the rules, the `when` blocks in `PostUi.kt` (icons) |
| Show only friends' stories in a different order | `HomeActivity.showStories` (the `sortedWith` list) |

## 4. Demo script on two devices (10 minutes)

1. Both devices on Log in. A signs up (show strength bar), picks a username; B tries the same name (refused), then another.
2. A searches B by half a name, sends a request; B's bell badge appears; B confirms.
3. A posts 5 photos: show the collage with "+2" on B; B reacts with Love, then changes to Haha (one reaction); comment, reply, like, photo comment.
4. A shares a story (photo, text), then a video; B opens it, reacts, replies (shows in chat).
5. Chats: green dot, "Active now"; swipe B's app away: A sees offline. Send text, photo, shared post; reply, heart, edit and delete a fresh message; wait or show an old message without those options.
6. Push: with the sender running, background B and send from A: notification arrives; tap opens the chat.
7. Voice call then video call; show the call line in the chat.
8. Screenshot on A: system line in chat + notification on B.
9. Log out: back stack cleared.

## 5. Before you submit (the assignment's own rules)

- [x] **Names** (done): project name `i221329_i221069`, package `com.kinnect.i221329_i221069` (group name `kinnect`; it is the
      `com.kinnect` part, change it with the same rename if your team uses another one). The ZIP and the folder you submit must be
      called `i221329_i221069_Assignment02`. Register the package name in Firebase when you create the project.
- [ ] **Private GitHub repository** (Settings > General > Danger zone > Change visibility). The current repository is public.
- [ ] **Commits from both members**, each feature in its own commit with a clear message. Your partner has to make their own
      commits from their own account; they cannot be created for them.
- [ ] No `google-services.json`, no `service-account.json`, no Agora App Certificate in the repository (all are git-ignored).
- [ ] Export: Android Studio > File > Export > Export to ZIP File.
- [ ] Two Espresso tests present (`BackendFlowTest`) and demo-ready on two devices; both members present.
