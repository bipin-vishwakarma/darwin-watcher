# Wedding Invitation Site

A single-page, mobile-first Indian wedding invitation. Opens with carved doors that
swing apart, then scrolls through the couple, the story, every function, a photo
gallery and a wishes wall.

No build step, no framework, no dependencies — three files and a folder of images.

```
wedding-invite/
├── index.html
├── assets/
│   ├── css/style.css
│   ├── js/config.js      ← the only file you need to edit
│   ├── js/main.js
│   ├── img/              ← drop photos here
│   └── audio/            ← drop theme.mp3 here (optional)
└── README.md
```

---

## 1. Put in the real details

Open **`assets/js/config.js`**. Everything the page shows lives there: names,
parents, the wedding date, each function with its venue and dress code, the story
beats, gallery captions, contact numbers and the closing line. Change the values,
reload the page, done. Nothing else needs to be touched.

A few notes:

| Field | Format | Notes |
|---|---|---|
| `weddingDate` | `"2026-12-06T19:30:00"` | 24-hour, venue-local time. Drives the countdown. |
| `events[].date` / `endDate` | same | `endDate` is optional; it only affects the calendar file. |
| `events[].icon` | `haldi` · `mehendi` · `sangeet` · `wedding` · `reception` · `ring` · `generic` | Picks the emblem on the card. |
| `events[].venue.mapUrl` | any URL | Easiest: search the venue on Google Maps, hit Share, paste the link. |
| `share.url` | the public URL | Used by the WhatsApp button. Leave `""` to use the current address. |

Add or remove entries in `events`, `story`, `gallery` and `contacts` freely —
each section builds itself from the array.

## 2. Add the photos

Drop the files into `assets/img/` using the names already listed in the config
(`bride.jpg`, `groom.jpg`, `gallery-1.jpg` …), or change the paths in the config
to match your filenames.

**You can ship the site before the photos arrive.** Every image is probed before
it is used — anything missing quietly falls back to a decorative gold panel, so
the page never shows a broken image.

Keep each photo under ~400 KB (resize to about 1200 px on the long edge) — most
guests will open this on mobile data.

## 3. Background music (optional)

Put an `.mp3` at `assets/audio/theme.mp3`. A music button appears at the bottom
right and starts playing when the guest opens the doors. If the file isn't there,
the button never appears and nothing breaks.

Use something you have the right to use — a shehnai or sitar loop from a
royalty-free library is the usual choice.

## 4. The wishes wall

Out of the box the wall runs in **local mode**: a guest's blessing is saved in
their own browser only, so they see their message but nobody else does. That's
fine for a demo, not for the real thing.

To collect wishes for real, wire it to a free Google Sheet:

1. Create a Google Sheet. In row 1 put the headers: `timestamp`, `name`, `message`.
2. **Extensions → Apps Script**, and replace the contents with:

   ```js
   const SHEET = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];

   function doPost(e) {
     const name = (e.parameter.name || '').slice(0, 60);
     const message = (e.parameter.message || '').slice(0, 400);
     if (name && message) SHEET.appendRow([new Date(), name, message]);
     return ContentService.createTextOutput('ok');
   }

   function doGet() {
     const rows = SHEET.getDataRange().getValues().slice(1);
     const wishes = rows.map(r => ({ name: r[1], message: r[2] }));
     return ContentService
       .createTextOutput(JSON.stringify(wishes))
       .setMimeType(ContentService.MimeType.JSON);
   }
   ```

3. **Deploy → New deployment → Web app**. Set *Execute as* to **Me** and
   *Who has access* to **Anyone**. Copy the `/exec` URL it gives you.
4. Paste that URL into `wishes.endpoint` in `config.js`.

Wishes now land in the sheet and appear on the wall for everyone. If the network
call ever fails, the guest's message is kept in their browser and they still get a
thank-you rather than an error.

Moderation: the wall renders whatever is in the sheet, and every message is
escaped before it is inserted, so a guest cannot inject markup. Still, delete a
row in the sheet and it's gone from the site on the next load.

## 5. Publish it

Any static host works — there is no server side.

**GitHub Pages** — push this folder to a repo, then Settings → Pages → deploy from
branch, and point it at the folder. The site is live at
`https://<user>.github.io/<repo>/`.

**Netlify or Vercel** — drag the `wedding-invite` folder onto the dashboard. Done
in about ten seconds, and you get a custom domain if you want one
(`aarav-ananya.com` reads better on a printed card than a subdomain).

Once it's live, paste the final URL into `share.url` in `config.js` so the
WhatsApp button always shares the canonical address.

## What's built in

- **Carved doors opener** with a rotating mandala and the Ganesha invocation
- **Live countdown** to the muhurat, which swaps to a thank-you line on the day
- **Couple, story timeline, and function cards** — each with venue, dress code,
  a Directions link and an **Add to calendar** button that generates a real
  `.ics` file in the browser
- **Photo gallery** with a tap-to-enlarge lightbox
- **Wishes wall** (Google Sheet or local)
- **WhatsApp share** button
- **Background music** toggle
- Falling marigold petals, scroll reveals, and a full `prefers-reduced-motion`
  path for guests who ask their phone for less animation
- Works offline-ish: the only external request is Google Fonts, and the type
  stacks fall back to system serifs if that request fails

## Re-skinning it

All colour and type lives in the `:root` block at the top of
`assets/css/style.css`. Swapping `--maroon`, `--gold` and `--marigold` re-skins
the entire site — doors, cards, petals and all.
