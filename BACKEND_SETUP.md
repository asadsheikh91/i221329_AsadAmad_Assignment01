# Kinnect backend: setup

The backend is Firebase: **Authentication** (accounts) and **Cloud Firestore** (profiles, friends, chats,
posts, stories, notifications, marketplace, call set-up). Voice and video calls use **WebRTC**, with Firestore
carrying the set-up messages. There is no server of your own to run, and the layouts of the app were not touched:
every screen of the design is now connected to real data.

## One-time setup (about 5 minutes)

1. Go to <https://console.firebase.google.com> and create a project.
2. **Add an Android app** to it with the package name `com.asadamad.i221329`.
3. Download **`google-services.json`** and put it in the `app/` folder (next to `app/build.gradle.kts`).
   The project will not build without it.
4. **Build > Authentication > Get started > Sign-in method**: enable **Email/Password**.
5. **Build > Firestore Database > Create database** (start in production mode, any region).
6. In Firestore, open the **Rules** tab, paste the contents of `firestore.rules` and **Publish**.
7. Sync Gradle in Android Studio and run on **two devices** (or a device and an emulator).

## Trying it out

1. On each device, **Create new account** with a different email (password: 6+ characters).
2. Device A: search for the other person, send a friend request.
3. Device B: Friends tab > **Confirm**.
4. Either device: open the chat (Home > chat icon, or the profile's **Message** button), send messages.
5. Home > "What's on your mind" to post text, or the gallery icon to add a photo. Like and comment on
   posts from the feed; you can delete your own posts with the X.
6. In the chat, tap the **phone** icon for a voice call or the **camera** icon for a video call.
   The other device rings; tap the green button to answer, the red one to hang up.
7. **Stories**: Home > **Create story** > shutter (opens the phone's camera) or the gallery thumbnail (photo picker).
   In the editor add text (Aa), an emoji, an effect or tag friends, then share with **Your story** (all friends),
   **Close friends** (you pick who) or the arrow. Friends see it on their Home row for 24 hours; they can reply
   (it lands in your chat) or react (you get a notification). Open your own story to see who saw it.
8. **Notifications** (bell): likes, comments, friend requests (with Confirm / Delete), accepted requests and story
   reactions. The red number on the bell and on the chat button are live counts of what is unread.
9. **Reactions**: hold the Like button on a post to pick Love, Haha, Wow, Sad or Angry.
10. **Marketplace**: **Sell** lists something (photo, price, place, category), **Categories** and the place at the top
    filter the grid, tap a listing to message the seller; your own listings can be marked sold or deleted.
11. **Profiles**: Edit profile saves username, website, bio and cover photo (phone and gender are private). Friend
    counts, mutual friends, join date and recent photos on a profile are real.

## How it works

| Piece | File |
| --- | --- |
| Accounts, profiles, friends, chat, call documents | `Backend.kt` |
| One WebRTC call (offer/answer, ICE, camera, microphone) | `WebRtcCall.kt` |
| Ringing, accept/decline, timer, mute, hang up | `BaseCallActivity.kt` |
| Voice call screen / video call screen | `VoiceCallActivity.kt`, `VideoCallActivity.kt` (`fragment_call.xml`, `activity_video_call.xml`) |
| Feed post rows, reactions and photo handling | `PostUi.kt`, `PostDetailActivity.kt` |
| Pictures stored as text, filters | `ImageTools.kt` |
| Notifications (backend, screen, bell badge) | `Notifications.kt`, `NotificationsActivity.kt`, `Navigation.kt` |
| Stories (backend, camera, picker, editor, viewer, your story, player) | `Stories.kt`, `StoryCameraActivity.kt`, `PhotoPickerActivity.kt`, `StoryEditorActivity.kt`, `StoryViewerActivity.kt`, `MyStoryActivity.kt`, `StoryPlayer.kt` |
| Marketplace (backend and screen) | `Marketplace.kt`, `MarketplaceActivity.kt` |
| Profile texts, finding views that have no id | `ProfileText.kt`, `ViewTools.kt` |
| Notices an incoming call while the app is open | `KinnectApp.kt` (`CallWatcher`) |
| Who may read and write what | `firestore.rules` |

Call flow: the caller creates `calls/{id}` with an `offer` and status `ringing`. The callee's phone sees it
(`CallWatcher`), opens the call screen, and on **Accept** writes an `answer` and sets the status to `accepted`.
Both sides exchange network addresses (ICE candidates) through `callerCandidates` / `calleeCandidates`,
and the audio/video then flows directly between the phones.

## Known limits

- **Incoming calls only ring while the app is open.** Ringing a closed app needs push notifications
  (Firebase Cloud Messaging plus a small Cloud Function). The same goes for notifications: they show up inside the
  app, not as phone notifications.
- **No TURN server.** Calls use Google's free STUN servers, which works on most Wi-Fi and home networks
  but can fail between phones on strict mobile networks. Add a TURN server in `WebRtcCall.ICE_SERVERS` if needed.
- **Pictures live inside Firestore documents** as shrunken Base64 JPEGs (no Firebase Storage, which needs a paid plan):
  posts up to about 1024 pixels, stories 1080 pixels (plus a small 360 pixel copy for the Home row), listings 480
  pixels, cover photos 1000 pixels. Fine for a class project; a real app should use Storage.
- **Who may see a story is decided on the phone**, not by the security rules: the rules let any signed-in user read
  stories, the app only shows friends' stories (and close-friends stories to the people on that list).
- Friend counts and mutual friends come from a list of friends' ids on each profile that is updated when a request
  is confirmed or an unfriend happens. If one of those two writes is interrupted the count can be off by one.
- Deleting a post leaves its comments behind in Firestore, and comments are a flat list (no replies).
- Profile pictures are the coloured initials (the "change profile photo" buttons say so). Stories "Highlight" and
  "Send", the editor's "Music", the menu's Memories / Saved / Groups / Events and dark mode have no data behind
  them in the design; they tell the user they are not available yet.
- Sign-in on a phone needs a real Firebase project: this folder has no `google-services.json`, on purpose.
- `NavigationFlowTest` (androidTest) assumed the old dummy login and needs a real test account to pass again.

## Tests

- `./gradlew testDebugUnitTest` runs the plain-logic tests (`HelpersTest`).
- The security rules were tested with the Firebase emulator against every kind of write the app makes (65 checks:
  profiles, friend lists, friendships, notifications, posts and reactions, chats, stories, listings).
