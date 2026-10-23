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

### My likes
- The final version overall ("Good job! I like this!"). This was the old version from the lost incognito chat.
- The rebuilt page in this repo, `index.html`: "I LOVE this. It's 100x better than the previous one."
- The dot globe in the rebuilt page: "don't you DARE TOUCH THE CIRCLE. ITS SO GOOD".
- The springy, bouncy dots that wobble and settle back.
- The rotating 3D dot globe centered behind the icon, at its original (smaller) size.
- The serious gray and teal look with the grid floor and layered 3D tilt.
- Wanting next: turning it into an app on my home screen.

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

### The current page (`index.html`), in case it ever needs rebuilding
- Dark gray and teal, a moving grid floor, system fonts only (nothing loaded from other sites).
- Top: "PrivateMuch" wordmark. Then the eye icon with a line through it, in front of the rotating 3D dot globe.
- Globe: dots spring away from the finger and glow, the whole globe leans toward the finger, a tap bounces it with a ripple, tapping the middle makes the eye blink. Only touches on the globe count, it keeps spinning while held, and dragging it never scrolls the page.
- Headline "Search privately", then the search bar (DuckDuckGo). Teal focus glow with a 3% lift and 16px text. The box clears itself after every search.
- Teal text selection and teal typing cursor.
- 3D info card that tilts where you press it: what's deleted when you close a private window, who can still see what you do, and a live check showing 0 bytes stored and 0 cookies.
- Stores nothing: no cookies, no storage, no trackers, no referrer sent to sites.
- Home screen app: `manifest.webmanifest` plus icons in `icons/` (the dot globe with the eye). Hosted on GitHub Pages at https://nucleanyt.github.io/PrivateMuch/. Chrome can't install apps from an incognito tab, so add it from a normal tab.
