# Google Play listing

What people read on the app's page on Google Play, and every value typed into Play Console for
it, kept here so the listing says what the app does, no more and no less, and changes with it.
Each field that is pasted is in a block, ready to paste as it is. The listing is in English, like
the app.

**Where it stands (2026-11-27).** The developer account exists: a personal account, made on
2026-11-27, its identity document with Google for verification. The app is not created in the
console yet; that waits for the verification and the contact phone. The texts and the
[graphics](#graphics) are ready. Nothing here has been submitted.

Three things keep it true, as for the desktop app's listing in the Microsoft Store:

- **At every release**, the release notes say exactly what changed (the owner's rule,
  2026-11-08), and the screenshots are retaken on the version released.
- **A change that makes a sentence here untrue** changes the sentence in the same branch.
- **It never names the project's site.** The site is kept apart from the apps' users (the
  owner's rule, 2026-10-21): the listing's website field stays empty.

What is checked on the build before any of this, and why each answer below is true, is in
[STATUS, "Before the first upload to Google Play"](STATUS.md#before-the-first-upload-to-google-play).

## Contents

- [The developer account](#the-developer-account)
- [Creating the app](#creating-the-app)
- [Store listing](#store-listing)
- [Graphics](#graphics)
- [Store settings](#store-settings)
- [App content](#app-content)
- [The bundle and its signing](#the-bundle-and-its-signing)
- [Testing before production](#testing-before-production)
- [Release notes](#release-notes)
- [Production](#production)
- [Still to decide](#still-to-decide)

## The developer account

Made by the owner on 2026-11-27, at play.google.com/console/signup.

| Field | Value | Why |
|---|---|---|
| Account type | Personal ("Yourself") | One person publishes one app |
| Owner | The project's Google account, noteorganizer.app@gmail.com | Kept apart from personal mail; it cannot be changed once the account exists |
| Developer name, legal name | Alexandra Moldovan (Google shows ALEXANDRA MOLDOVAN, as in the payments profile) | The same publisher as in the Microsoft Store |
| Country | Romania | |
| Public contact email | noteorganizer.app@gmail.com | Shown on the app's page, the address the privacy policy gives |
| Trader status | Non-trader | A free app, with no ads and nothing to buy, not a business. The EU's Digital Services Act asks; a trader's address and phone would be shown publicly. It can change if the app ever earns money |
| Earning money | No | |
| Apps planned in 12 months | 1 | |
| App categories asked about | None of the above | Not for children, not financial, health, news or government |
| Website (not public) | The Android repository on GitHub | Not the project's site |

The registration fee was paid once. Still to do on the account: the identity check (Google's,
a few days), then the contact phone's code by SMS, then the app can be created. The Android
device check (the Play Console app signed in on a real phone) is done.

New personal accounts cannot publish straight to production: a closed test comes first
([Testing before production](#testing-before-production)).

## Creating the app

| Field | Value |
|---|---|
| App name | Note Organizer |
| Default language | English (United States), en-US |
| App or game | App |
| Free or paid | Free. An app published free cannot be made paid later |
| Declarations | The Developer Program Policies, and US export laws: both ticked |

The package name is the bundle's `applicationId`, `ro.noteorganizer.app`, and it is fixed for
good with the first upload: Google Play never accepts another app under it, nor this app under
another. Its page will be `https://play.google.com/store/apps/details?id=ro.noteorganizer.app`.

## Store listing

### App name

Up to 30 characters.

```
Note Organizer
```

### Short description

Up to 80 characters; shown under the name in search and on the page.

```
Offline notes: tables, checklists, repeating notes and a calendar. No account.
```

### Full description

Up to 4,000 characters. Plain text, as on the desktop's listing: the sections are capital words
and the lists are bullets.

```
Note Organizer is a notes app that keeps everything on your own phone. There is no account to make, no cloud, no advertising and no tracking, and the app has no internet permission: it opens a page in your browser only when you ask it to.

WRITE NOTES THE WAY YOU WANT THEM
• Bold, italic, underline, strike, colours, highlights, sizes and fonts, lists and alignment, in a note that saves itself, with Undo and Redo.
• Tables written right in their cells, with captions and column widths.
• A checklist in any note: tick items off, and drag them into order.
• Comments, attached files, and a note's category, colour, date and pin.
• Copy and paste through the app's menu keep the formatting, from a web page or into an email.
• Find in a note, a calculator under it, and templates for the notes you write again and again.

NOTES THAT REPEAT, AND A CALENDAR
• Notes that repeat daily, weekly, monthly or yearly, forever or until an end you set.
• Change one day of a series on its own, or move it to another date.
• A month that shows each day's notes, with painted days and markers, and a History of everything that was on it.
• The day's reminder: a bar when the app opens on a day with notes and, if you turn it on, a notification at the hour you choose. It says how many notes, never their titles.

FIND AND ORGANISE
• Search every note at once, checklists included, with or without diacritics.
• Sort, filter, group by category, or arrange the list by hand.
• Select several notes to pin, move, share or delete them together; a move or a delete can be undone.
• Find and clean up duplicate notes.
• A Trash to restore from, emptied on its own.

SHARE AND MOVE YOUR NOTES
• Send a note through your phone's share sheet: as a PDF, a web page, its words, or a share code. The app sends nothing itself.
• Move notes to and from Note Organizer for Windows: one note as a share code, or the whole notebook as a backup .zip. A note that arrives where a copy of it already lives asks you first: update it, or add it as a new one.

ON A PHONE OR A TABLET
• Upright or on its side. On a tablet the tabs move to a rail on the left, and on a wide screen the note opens beside the list.
• Eight themes, four light and four dark, under one Light | Dark switch.

GOOD TO KNOW
• It does not sync: notes move between devices when you send them.
• A share code is not encryption: anyone holding it can read the note.
• Your notes stay in the app's private storage. Uninstalling the app deletes them from the phone, so make a backup first.
• While your phone's backup to your Google account is on, Android copies the app's notes there too; Settings → Backup turns the app's part off.
• It needs Android 8.0 or later.

To report a problem or share an idea, write a review here on Google Play. I read every one and answer here, publicly.
```

Every claim was checked against the [README](../README.md), [STATUS](STATUS.md) and the privacy
policy on 2026-11-27, at 0.15.2. Note Organizer for Windows is named, the Microsoft Store is
not: the listing describes this app, and a store page is no place to send people to another
store.

## Graphics

Each is made from the app itself or drawn by code, like the launcher icon: nothing comes from a
template or a stock library. Three commands make them all, into `build/play-listing/`, which git
ignores; they are remade rather than kept, like the desktop's pictures for the Microsoft Store
([how](screenshots/README.md#google-plays-set)):

    py tools/emulator/take_screenshots.py --play    1-note-list.png ... 8-appearance.png
    py tools/make_launcher_icon.py --play           icon-512.png
    py tools/make_feature_graphic.py                feature-graphic.png

| Asset | Google Play's rule | What it is |
|---|---|---|
| App icon | 512 × 512 px, 32-bit PNG with alpha, at most 1 MB; full square, Google Play rounds its corners and adds the shadow | The launcher icon's drawing (the desktop's `tools/make_icon.py`) on its ground as a full square, the mark at 70% of the side, since Google Play cuts only the corners |
| Feature graphic | 1024 × 500 px, JPEG or 24-bit PNG, no alpha; shown on the page and where Google Play features the app | The app's name and "Offline notes, on your phone" on Midnight Purple, the note list on the right (the owner's choice, 2026-11-27); the colours from `Themes.kt`, the letters in Roboto, Android's own font |
| Phone screenshots | 2 to 8; JPEG or 24-bit PNG, no alpha; each side 320 to 3,840 px, the longer at most twice the shorter; Google Play's help asks for at least four at 1080 px or more, 9:16, for the app to be eligible for its recommendations | Eight at 1080 × 1920, on the emulator, on a notebook written for the camera |
| Tablet screenshots | Optional, 7-inch and 10-inch | Not made; the same script at a tablet's size, if the owner wants the page to show the tablet layout |

The README's own set in `docs/screenshots/` is 1080 × 2400, whose longer side is 2.22 times the
shorter, and keeps its alpha, so Google Play would refuse it; the rule on the longer side is read
from Google Play's help on preview assets
([Add preview assets](https://support.google.com/googleplay/android-developer/answer/9866151)),
and the console says it again when one is refused. `--play` sets the emulator's screen to
1080 × 1920 for its run instead, and puts it back.

The set, in the order it is uploaded, all in the light theme but one: the note list, a note with
its checklist, a table, writing with the format bar (the note's title scrolled out of view above
the keyboard, as on a 9:16 phone), the calendar, search, the list in Midnight Purple, Settings →
Appearance. **Made on 2026-11-27 for 0.15.2**, each picture looked at.

## Store settings

| Field | Value |
|---|---|
| App category | Productivity |
| Tags | Chosen in the console from Google Play's list, up to five, the ones about notes and productivity |
| Contact email | noteorganizer.app@gmail.com (required) |
| Contact phone | Empty |
| Website | Empty: the project's site is kept apart from the apps' users |

## App content

Policy → App content, each a form of its own.

| Form | Answer | Why |
|---|---|---|
| Privacy policy | `https://sites.google.com/view/noteorganizer-privacy` | The one page for both apps, `Links.PRIVACY`, compared with the desktop repository's `docs/PRIVACY.md` at every release |
| App access | All functionality is available without special access | There is no account or sign-in |
| Ads | No, the app contains no ads | |
| Content rating | The IARC questionnaire, below | |
| Target audience and content | 13 and over: 13–15, 16–17, 18 and over; not designed to appeal to children | Under 13 would bring Google Play's Families policy, made for apps built for children |
| News app | No | |
| COVID-19 contact tracing and status | Not such an app | |
| Data safety | No data collected, no data shared | Decided on 2026-10-20; the reasoning, point by point, is in [STATUS](STATUS.md#before-the-first-upload-to-google-play) |
| Government app | No | |
| Financial features | None | |
| Health | None | |
| Advertising ID | No | The release build's merged manifest asks for no `AD_ID` permission (read on the build of 2026-11-23) |

### Permissions

The release build asks for two permissions, both for Settings → Reminders, which stays off until
the person turns it on: `POST_NOTIFICATIONS` (the day's count) and `RECEIVE_BOOT_COMPLETED` (to
set the next reminder again after a restart). Neither needs a declaration form. The manifest also
carries `ro.noteorganizer.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which Android's own
libraries add and which the privacy policy explains. No exact alarms, no photos or videos
permission, no foreground service, no access to all files.

### Content rating (IARC)

The questionnaire gives the ratings for every region at once (PEGI, ESRB, USK and the rest); the
desktop app's came out 3+. Its email is the project's address. Category: the one for utility
and productivity apps, not a game, a social network or a news app; its exact name is read on the
form.

| Question, in short | Answer | Why |
|---|---|---|
| Violence, sexuality, language, controlled substances, gambling, horror | No | |
| Users interact or exchange content with each other in the app | No | Nothing passes between people inside the app; a note leaves only through the phone's share sheet, to an app the person picks |
| Shares the user's location | No | The app has no location permission |
| Digital purchases | No | |
| Unrestricted internet access, such as a browser | No | The app has no internet permission; it opens a page in the phone's browser only when asked |

The one judgement among them is the second: a share code or a PDF sent through the share sheet is
the person's own message in another app, not an exchange inside this one.

## The bundle and its signing

- **An app bundle**, `.aab`, built in release mode with R8; checked already on the build: target
  SDK 37, not debuggable, no internet permission, 16 KB alignment, no `<queries>`
  ([STATUS](STATUS.md#before-the-first-upload-to-google-play)).
- **Play App Signing.** Google keeps the key that signs what people install; the bundle is
  uploaded signed with an **upload key**, made once with the owner and kept outside the
  repository. The build is ready for it: a properties file outside the repository names the
  keystore and its passwords, and `noteorgUploadKey` in the user's own
  `~/.gradle/gradle.properties` (or `NOTEORG_UPLOAD_KEY`) names that file
  (`app/build.gradle.kts`). Without it the release build signs with the debug key, as before.
- **The owner's phone** holds a build signed with the debug key, so the store's build cannot
  install over it: it is uninstalled first, and an uninstall deletes the notebook. Its notes go
  to a backup `.zip` first; and the app's next start empties the Trash past its window, so the
  owner is told before.
- **The bundle and its `mapping.txt` kept, once per version**, outside the repository, so a
  crash's trace from an older version can still be read: `gradlew.bat bundleRelease`, then
  `py tools/keep_release.py`, which refuses a debug-signed bundle, one older than the code, and a
  version kept already.
- **The version at launch is 1.0.0** for both apps (the desktop's ROADMAP,
  [At the real launch](https://github.com/AlexandraMoldovan95/noteOrganizer/blob/main/docs/ROADMAP.md#at-the-real-launch-100-and-after)),
  computed and shown to the owner before it is set.
- **Android developer verification.** Play Console's account page has it: package names
  registered to a verified developer. What it asks for an app distributed on Google Play is read
  there when the app is created.

## Testing before production

A personal account made after 13 November 2023 must run a **closed test with at least 12 testers,
opted in for the 14 days in a row before it applies for production**; production stays disabled
until then. A tester who leaves before the 14 days does not count, and Google may still ask for
more testing after it reviews the application
([App testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)).

How it runs, once the app exists in the console:

1. **Internal testing** first, optional and quick (up to 100 people, available within minutes
   rather than after a review): the owner
   installs from Google Play what Google Play builds from the bundle, which is not the APK the
   emulator walked.
2. **The closed test track.** Its testers are a list: an email list (Manage account → Tester
   groups) or a Google Group. The track gives an **opt-in link**; a tester opens it with the
   Google account on their phone, accepts, and installs from Google Play.
3. **The testers are gathered at this step, not before** (the owner's choice, 2026-11-27), with a
   link she can send. A Google Group does that best: people join the group from its own link,
   and the opt-in link then works for them; with an email list, each address is typed in first.
4. **Fourteen days**, with the app kept installed and used. Notes kept during the test (what
   testers said, what changed) answer the questions of the next step.
5. **Apply for production**: a short questionnaire about the test, then Google's review.

The test plan's Google Play area (cases 545 to 549) is walked along the way: the install from
the closed test with the notebook back from a backup, an update keeping every note, the page
saying what this file says, the pre-launch report, and Write a review once the page is live.

## Release notes

Up to 500 characters per language, saying exactly what changed. For the first release, on the
closed test and in production:

```
The first release of Note Organizer for Android.
```

## Production

- **Countries**: all that Google Play serves.
- **A staged rollout**: a share of people first, raised after a few quiet days, with crashes and
  "isn't responding" watched; halted first when something is wrong. The steps are written in the
  desktop repository's
  [Android release reference](https://github.com/AlexandraMoldovan95/noteOrganizer/blob/main/docs/RELEASE_ANDROID.md#rolling-out-and-stopping-a-release).
- **Once the page is live**, `Links.STORE` takes its address, and Settings → About shows
  Feedback with Write a review: a change of its own, with its version.
- **The privacy page** is compared with `docs/PRIVACY.md` before the build goes out, as at every
  release.

## Still to decide

The owner's, each when its step comes:

- The tags, from Google Play's list.
- Whether the page shows tablet screenshots.
- Internal testing first, or straight to the closed test.
- An email list or a Google Group for the testers.
