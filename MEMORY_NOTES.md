## Memory notes (preferences, likes, and Claude's fails)

### My preferences
- Rule number one: double check everything.
- Pronouns: he/him. Claude Pro user.
- I'm usually on my phone, so things need to be built for a phone screen and touch.
- I like 3D effects and lots of motion, but in a serious, clean style (not goofy).
- I wanted it to look like Google's incognito page; Claude won't copy that design, so it stays original.
- Effects should only react where I'm actually touching, not anywhere on the screen.
- Interactions should feel gentle and bouncy, never aggressive.
- When I ask for a file, I want everything included.
- It shouldn't look "vibecoded" (the generic AI look): no buttons that do nothing, no gimmicks, plain and honest wording.
- NEVER change the dot globe unless I ask. It's perfect as it is.
- I want a real standalone Android app (I'm on a Mi 10T), not just a website shortcut, that works like a proper private browser: address bar, tabs, a bottom bar.
- Updating should be easy: one Update button in the app, no hunting for links.
- Adult content must be blocked, including the plain word "sex".
- I want a choice of search engine: DuckDuckGo or Brave.
- Bookmarks are only shortcuts and must not save any data: just each site's address is kept (no titles, page data, cookies or history), only inside the app, never backed up or sent anywhere, removable one by one or all at once. Search results can't be bookmarked, because their address holds what I searched for.
- When I send a screenshot with something circled, look closely at exactly that spot.

### My likes
- The final version overall ("Good job! I like this!"). This was the old version from the lost incognito chat.
- The rebuilt page in this repo, `index.html`: "I LOVE this. It's 100x better than the previous one."
- The dot globe in the rebuilt page: "don't you DARE TOUCH THE CIRCLE. ITS SO GOOD".
- The springy, bouncy dots that wobble and settle back.
- The rotating 3D dot globe centered behind the icon, at its original (smaller) size.
- The serious gray and teal look with the grid floor and layered 3D tilt.

### Claude's fails (and the fixes)
1. Dots moved toward my finger instead of away ("IM GONNA KILL YOUUUU"). Cause: dots were pushed out from the globe's center, so ones between the center and my finger moved at me. Fixed by pushing directly away from the finger, softer and smoothed.
2. Globe and page tilt reacted to touches anywhere on the screen. Fixed by limiting the globe to touches on the globe and the tilt to the info card.
3. Globe froze when I held my finger on it, and drags got stolen as page scrolls. Fixed by keeping it spinning and locking the drag to the globe.
4. The dot reaction was too small and hidden under my fingertip. Fixed by widening the zone and adding brightness and ripples.
5. Overcorrected by making the globe too big, and lost the springy feel I liked. Fixed by restoring the size and adding real spring physics.
6. The search bar showed an ugly default focus box. Fixed with a custom teal focus effect and 16px text to stop iPhone zoom.
7. Dragging was hard to start and the tap effect kept replaying mid-drag. Cause: phone touch cancels were treated as taps, plus extra hold rings. Fixed by rebuilding on raw touch events with a strict tap rule.
8. The focused search bar grew about 14% wider and got cut off at the edges. Fixed by shrinking the lift to about 3% and adding side padding.
9. The first .md file left out the older versions, and Claude misread "memories" as only saved memories (none exist in incognito chats).
10. Only about 10% of the incognito chat got saved. Claude can't open incognito chats, and that chat is gone, so the page was rebuilt from my description.
11. The first rebuild cut off the search placeholder text ("...with Duc"). Fixed by shortening it to "Search".
12. Bugs found in the cleanup pass, all fixed: button press effects didn't show on iPhone; the card tilt could follow a finger that wasn't on the card; the keyboard focus ring was hidden inside the search bar; the hidden clear button could still be reached with Tab; the page said "private mode" even in a normal window.
13. Typing a web address (like "nucleusbot.netlify.app") searched DuckDuckGo for it instead of opening the site. Fixed: addresses now open directly, like a browser's address bar, also when typed into DuckDuckGo's own box in the app. It only counts real endings (.com, .app, the real country endings and so on), so "node.js" is still a search. Adult sites typed as addresses are blocked too.
14. The files first only went to a side branch, so the repo's front page (the main branch) showed just the license and readme. Fixed by merging into main; every change now gets merged into main.
15. In Settings, the teal outline of the selected search engine card was cut off on the right. Likely causes: the outline got thicker when picked, which can spill past the card's edge, and the card's press bounce briefly made it bigger than its space. Fixed both: outlines keep the same thickness and only change color (the address bar too), big cards squish much less, and cards are never clipped while bouncing.

### The current app and page, in case they ever need rebuilding
- Dark gray and teal, a moving grid floor, system fonts only (nothing loaded from other sites).
- Top: "PrivateMuch" wordmark. Then the eye icon with a line through it, in front of the rotating 3D dot globe.
- Globe: dots spring away from the finger and glow, the whole globe leans toward the finger, a tap bounces it with a ripple, tapping the middle makes the eye blink. Only touches on the globe count, it keeps spinning while held, and dragging it never scrolls the page.
- Headline "Search privately", then the search bar (DuckDuckGo or Brave, see Settings below). Teal focus glow with a 3% lift and 16px text. The box clears itself after every search.
- Teal text selection and teal typing cursor.
- 3D info card that tilts where you press it: what's deleted when you close a private window, who can still see what you do, and a live check showing 0 bytes stored and 0 cookies.
- Stores nothing: no cookies, no storage, no trackers, no referrer sent to sites.
- Home screen app: `manifest.webmanifest` plus icons in `icons/` (the dot globe with the eye). Hosted on GitHub Pages at https://nucleanyt.github.io/PrivateMuch/. Chrome can't install apps from an incognito tab, so add it from a normal tab.
- Standalone Android app (I'm on Android, Mi 10T): the source is in `android/`. GitHub builds it on every push and puts it at https://github.com/NucleanYT/PrivateMuch/releases/download/latest/PrivateMuch.apk. The page is built in, so it works without a website. Cookies, site data, cache and history are wiped every time it starts and closes. Search results open inside the app. The keyboard doesn't learn what I type, and nothing is backed up.
- Search / AI switch above the search bar: with DuckDuckGo, Search opens DuckDuckGo results and Duck.ai sends the text to Duck.ai with DuckDuckGo's "!ai" shortcut (empty sends you straight to duck.ai); with Brave it reads "Search | Ask Brave". It always starts on Search.
- In the Android app, an address bar slides in whenever a website is open: Home button, a rounded field showing the site's name with a lock (teal for secure sites), and reload. Tap the field to type a web address or a search (same rules and adult filter as the home page); the outline turns teal and it lifts with a small bounce. A thin teal line shows page loading. Home goes back to the globe page.
- Bottom bar in the Android app while a site is open (I said the bottom looked dull): Back, Forward (both fade when unavailable), Bookmarks (the icon fills teal when the open page is saved), Tabs with the count, New tab. It hides while typing in the address bar.
- Tabs in the Android app (up to 12): a rounded-square tabs button with the count, in the bottom bar and on the home page (top-left: Tabs, Bookmarks, Settings). It opens a full-screen grid of tab cards (preview, title, site, × to close; the current one has a teal outline) with a teal New tab button; cards rise in with a small bounce. Back closes the overview; on the first page of a tab, Back closes that tab and returns to the one before (with only one tab, Back closes the app). Tabs are memory-only and wiped on close.
- Update button in the Android app: a small pill in the top-right corner of the home page. It only checks when I tap it (nothing runs in the background). It compares the build number with GitHub's latest release ("PrivateMuch build N"), downloads the newer APK with a % counter, and opens Android's installer. The first time, Android asks me to allow PrivateMuch to install apps.
- Adult content filter: the same lists in `index.html` and `android/.../ContentFilter.java` (the build fails if they differ): about 115 blocked words and phrases, site-name parts (like "porn" in an address), and exact sites for names too common to match as parts ("erome.com", but not "stjerome.org"). Left out on purpose because of everyday meanings: "hooker" (rugby), "strap on" (helmets), "naked" alone (naked eye). It catches disguised spellings ("p0rn", "p o r n", accents) but only matches whole words, so "class", "Essex" or "naked mole rat" aren't blocked. Plain "sex" is blocked too, at my request (whole word only, so "Essex" and "unisex" still work). Strict Safe Search is on for every search (kp=1 on DuckDuckGo, safesearch=strict on Brave, including image, video and news search). The app also blocks adult sites (by address) and adult searches on any site, showing a calm "Blocked" page.
- Search engine setting (app, gear button on the home page, top-left next to Tabs; Update stays top-right): DuckDuckGo + Duck.ai (default) or Brave Search + Ask Brave. I asked for Brave's "Leon" AI; Brave's AI is called Leo and it only works inside the Brave browser, so the app uses Ask Brave (search.brave.com/ask), Brave's AI on the web. The setting and the bookmark addresses are the only things the app remembers. Brave searches get safesearch=strict (Brave doesn't document that link setting, so the word filter is the real guarantee).
- The tab overview has an incognito look: "Private tabs" with the eye-with-a-line badge, "history-free" next to the count, a small private badge on each card, a note that nothing is kept, and a Close all tabs button.
- Bookmarks screen (bottom bar or home page): "This page" card with a teal Bookmark button (or Bookmarked, which removes it; on search results it says searches can't be bookmarked), shortcuts below showing the site and page from the address (tap to open, × to remove), a note that a bookmark is only a shortcut, and Remove all bookmarks, which needs a second tap. Up to 200. Adult sites can't be bookmarked.

### How changes reach me
- Work happens on a branch, GitHub builds the app on every push (the build also checks that the page's and the app's shared lists match), and then it's merged into main so the repo page and the web version update too.
- In the app I tap Update to get the newest build. The download link always points at the newest build.
- Claude can't run the Android app itself, so new app screens are checked by me on my phone; screenshots help.

