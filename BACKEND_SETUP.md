# Kinnect backend: how it works

The backend is Firebase:

| Need | Service |
| --- | --- |
| Accounts (email and password) | **Firebase Authentication** |
| Users, posts, reactions, comments, stories, friend requests, chats, call logs, presence | **Firebase Realtime Database** |
| Push notifications for new messages | **Firebase Cloud Messaging (FCM)** |
| Voice and video calls | **Agora SDK** (the database only does the ringing) |

There is no server of your own to run, except the small notification sender (see below) that you start for the demo.
Step-by-step setup of your own Firebase and Agora projects is in **FIREBASE_CHECKLIST.md**.

## Where everything is

| Piece | File |
| --- | --- |
| Accounts, profiles, unique usernames, presence, search, friends | `Backend.kt` |
| Posts, six reactions, threaded comments | `PostsBackend.kt` |
| Chats: text, photos, shared posts, replies, hearts, edit / delete, screenshots | `ChatBackend.kt` |
| Notifications (the bell) | `Notifications.kt` |
| Stories (photo or video, 24 hours) | `Stories.kt`, `StoryPlayer.kt`, `StoryMedia.kt` |
| Marketplace | `Marketplace.kt` |
| Call set-up and call history | `CallBackend.kt` |
| One Agora call (join, mute, camera, hang up) | `AgoraCall.kt` |
| Ringing, accept / decline, timer | `BaseCallActivity.kt`, `VoiceCallActivity.kt`, `VideoCallActivity.kt` |
| Online / offline, ringing while the app is open | `KinnectApp.kt` |
| Push notifications on the phone | `KinnectMessagingService.kt` |
| Push notifications on the sender side | `notification-sender/` (Node, free) and `functions/` (Cloud Function) |
| Screenshot detection | `ScreenshotWatcher.kt` |
| Pictures as text, filters, video | `ImageTools.kt`, `PostUi.kt` (`PostImages`) |
| Profile pictures in every avatar | `Avatars.kt` |
| Who may read and write what | `database.rules.json` |
| Plain data classes and helpers | `Models.kt` |

## The database tree

```
users/{uid}                  name, nameLower, lastNameLower, username, usernameLower, email, bio, website, profileDone, createdAt
usernames/{name}             uid                 <- makes a username unique (the rules let only one person claim a name)
avatars/{uid}  covers/{uid}  profile picture, cover photo (Base64)
userPrivate/{uid}            phone, gender       <- only the owner can read it
presence/{uid}               online, lastSeen    <- written by the app, and by the server when the phone disconnects
friends/{uid}/{other}        true                <- both people have the entry
requests/{to}/{from}         time                <- friend requests I received
sent/{from}/{to}             time                <- friend requests I sent
posts/{id}                   authorId, authorName, text, imageCount, commentCount, createdAt
posts/{id}/reactions/{uid}   Like | Love | Haha | Wow | Sad | Angry     <- one key per person = one reaction each
postImages/{id}/{0,1,..}     the pictures of a post
comments/{postId}/{id}       authorId, text, createdAt, parentId (reply to), hasImage, likes/{uid}
commentImages/{id}
messages/{chatId}/{id}       sender, type (text|image|post|system), text, postId, replyToId/Text/Sender,
                             createdAt, editedAt, deleted, hearts/{uid}      chatId = the two uids sorted, joined by _
messageImages/{chatId}/{id}
userChats/{uid}/{other}      lastMessage, lastSender, lastTime, unread       <- what the Chats list shows
stories/{id}                 authorId, type (image|video), thumb, text, audience, audienceUids, viewedBy, likedBy, createdAt, expiresAt
storyMedia/{id}/{000,001..}  the picture or video, cut into 500 KB pieces
notifications/{uid}/{id}     fromUid, fromName, type, text, refId, read, createdAt
listings/{id}                marketplace listing
calls/{id}                   callerId, calleeId, video, status (ringing|accepted|ended|rejected|missed|busy)
callLogs/{uid}/{id}          peerUid, video, outgoing, status, seconds, time
fcmTokens/{uid}/{token}      true                <- where to push to
pushQueue/{id}               toUid, fromUid, title, body, kind          <- read only by the notification sender
```

## How the main flows work

- **Sign up -> profile setup.** Create account makes the Auth account and `users/{uid}` with `profileDone: false`, then opens
  Edit profile in setup mode. Save checks `usernames/{name}`: if somebody else owns it you get "That username is taken". The
  save is one atomic write (profile fields + the username claim), and the rules refuse a second owner, so two people can never get
  the same name. Only then does Home open.
- **Presence.** While any Kinnect screen is visible the app writes `presence/{uid}/online = true`. At the same time it asks the
  server to write `online = false` when the connection drops (`onDisconnect`), so a crash or a dead battery cannot stay "online".
  Log out writes offline and signs out. Chats shows a green dot, a chat shows "Active now".
- **Feed.** The 80 newest posts arrive live; Home keeps those of you and your friends, newest first. Pictures are loaded
  separately (`postImages`) so the feed itself stays small. Two or more pictures use the collage with "+N".
- **Chat.** A message and both chat-list entries are written in one atomic update. The other person's `unread` counter goes
  up with a server-side increment and is reset when the chat is opened. Edit and Delete exist for 5 minutes: the app hides them
  after that and the rules also refuse an edit later than 5 minutes (`now < createdAt + 300000`).
- **Screenshots.** Android 14 and later tell the app directly; older versions are detected by watching for a new "screenshot"
  picture in the gallery. The app then writes "Aisha took a screenshot" into the conversation, a notification and a push request.
- **Calls.** The caller writes `calls/{id}` with status `ringing` and joins the Agora channel named like the call. The other phone
  sees the call (`CallWatcher`), shows the call screen, and on Accept sets `accepted` and joins the same channel. Agora connects
  the two phones. Hang up sets `ended`. Each side saves its own call-history entry, and the caller leaves a "Voice call 2:13" line in the chat.
- **Push notifications.** Sending a message writes a request into `pushQueue`. The sender reads it, looks up the receiver's tokens
  in `fcmTokens` and asks FCM to deliver a *data* message. `KinnectMessagingService` on the receiver's phone shows the notification
  (also when the app is closed) and opens the chat when tapped.

## Known limits

- **Incoming calls only ring while the app is open.** Ringing a closed app would need a push for calls as well (the same mechanism).
- **Agora has no token server here.** The Agora project must be created in "App ID" (testing) mode; a production project with an
  App Certificate would need a token from a server.
- **The notification sender must be running** (laptop) or the Cloud Function deployed (needs the Blaze plan). Without it messages
  still arrive in the app, there is just no push.
- **Pictures and videos live inside the database** as Base64 text (no Firebase Storage, which needs a paid plan). Photos are shrunk
  (posts 900 px, stories 1080 px, listings 480 px, avatars 256 px); a story video can be about 10 seconds (4.5 MB).
- **Who may see a story is decided on the phone**, not by the rules (the rules let any signed-in user read stories).
- Deleting a post deletes its comments, but not the pictures of those comments.
- The "Mutual friends" and friend counts read other people's friend lists, which every signed-in user may read.
- Stories "Highlight" and "Send", the editor's "Music", voice messages, and the menu's Memories / Saved / Groups / Events say they
  are not available yet (nothing in the assignment needs them).
- Sign-in needs a real Firebase project: this folder has no `google-services.json` on purpose (never commit it).

## Tests

- `./gradlew testDebugUnitTest`: plain-logic tests (`HelpersTest`).
- `BackendFlowTest` (androidTest, two Espresso tests): log in against Firebase and create a post; see the comment at the top of the file.
- The database rules were tested with the Firebase emulator: 105 checks covering every write the app makes.
- The notification sender has its own test (`notification-sender/test.mjs`, uses the database emulator and a fake FCM).
