/* ==========================================================================
   Wedding invitation — behaviour
   Reads everything from WEDDING (assets/js/config.js). No build step, no
   dependencies. Safe to open straight from the filesystem.
   ========================================================================== */
(function () {
  "use strict";

  var C = window.WEDDING || WEDDING;
  var $  = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------------------------------------------------------------- dates */
  var MONTHS = ["January","February","March","April","May","June",
                "July","August","September","October","November","December"];
  var DAYS   = ["Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"];

  function parseDate(s) {
    if (!s) return null;
    // Parsed as local time on every browser (avoids the Safari/ISO-Z shift).
    var m = String(s).match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?)?/);
    if (!m) { var d = new Date(s); return isNaN(d) ? null : d; }
    return new Date(+m[1], +m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0));
  }
  function fmtLong(d)  { return d ? DAYS[d.getDay()] + ", " + d.getDate() + " " + MONTHS[d.getMonth()] + " " + d.getFullYear() : ""; }
  function fmtShort(d) { return d ? d.getDate() + " " + MONTHS[d.getMonth()].slice(0, 3) + " " + d.getFullYear() : ""; }
  function fmtTime(d) {
    if (!d) return "";
    var h = d.getHours(), m = d.getMinutes();
    var ap = h >= 12 ? "PM" : "AM";
    h = h % 12 || 12;
    return h + (m ? ":" + (m < 10 ? "0" + m : m) : "") + " " + ap;
  }
  function pad(n) { return n < 10 ? "0" + n : "" + n; }

  function txt(el, value) { if (el) el.textContent = value || ""; }
  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  /* ------------------------------------------------------ mandala geometry */
  function drawMandala(group) {
    if (!group) return;
    var ns = "http://www.w3.org/2000/svg", i, a, petals = 16;
    for (i = 0; i < petals; i++) {
      a = (360 / petals) * i;
      var p = document.createElementNS(ns, "path");
      p.setAttribute("d", "M100 46 C112 62 112 74 100 88 C88 74 88 62 100 46 Z");
      p.setAttribute("transform", "rotate(" + a + " 100 100)");
      group.appendChild(p);
    }
  }
  $$(".mandala__petals").forEach(drawMandala);

  /* ============================================================ 1. OPENER */
  var bride = C.couple.bride, groom = C.couple.groom;
  var weddingAt = parseDate(C.weddingDate);
  var mainEvent = (C.events || []).filter(function (e) { return e.icon === "wedding"; })[0] || (C.events || [])[0];

  txt($("#openerEyebrow"), C.opener.invitationLine);
  txt($("#openerBride"), bride.name);
  txt($("#openerGroom"), groom.name);
  txt($("#openerSub"), C.opener.subLine);
  txt($("#openerDate"), fmtShort(weddingAt));
  txt($("#btnOpenLabel"), C.opener.buttonLabel || "Open Invitation");

  document.title = bride.name + " & " + groom.name + " — Wedding Invitation";
  var ogTitle = $('meta[property="og:title"]');
  var ogDesc  = $('meta[property="og:description"]');
  var metaDesc = $('meta[name="description"]');
  var previewLine = bride.fullName + " & " + groom.fullName + " · " + fmtLong(weddingAt);
  if (ogTitle)  ogTitle.setAttribute("content", document.title);
  if (ogDesc)   ogDesc.setAttribute("content", previewLine);
  if (metaDesc) metaDesc.setAttribute("content", previewLine);

  /* ============================================================== 2. HERO */
  txt($("#heroBride"), bride.name);
  txt($("#heroGroom"), groom.name);
  txt($("#heroDate"), fmtLong(weddingAt));
  txt($("#heroVenue"), mainEvent ? mainEvent.venue.name + " · " + mainEvent.venue.address : "");
  txt($("#heroHashtag"), C.couple.hashtag);

  /* ========================================================= 3. COUNTDOWN */
  var cdGrid = $("#countdown"), cdNote = $("#countdownNote");
  var CD_UNITS = [["days", "Days"], ["hours", "Hours"], ["mins", "Minutes"], ["secs", "Seconds"]];

  cdGrid.innerHTML = CD_UNITS.map(function (u) {
    return '<div class="cd"><span class="cd__num" data-cd="' + u[0] + '">--</span>' +
           '<span class="cd__lbl">' + u[1] + "</span></div>";
  }).join("");

  function tickCountdown() {
    if (!weddingAt) return;
    var diff = weddingAt - new Date();
    if (diff <= 0) {
      cdGrid.hidden = true;
      txt(cdNote, "The day is here. Thank you for celebrating with us.");
      clearInterval(cdTimer);
      return;
    }
    var s = Math.floor(diff / 1000);
    var set = function (k, v) { var el = cdGrid.querySelector('[data-cd="' + k + '"]'); if (el) el.textContent = v; };
    set("days", Math.floor(s / 86400));
    set("hours", pad(Math.floor(s / 3600) % 24));
    set("mins", pad(Math.floor(s / 60) % 60));
    set("secs", pad(s % 60));
  }
  txt(cdNote, "until the pheras at " + (mainEvent ? mainEvent.venue.name : "the ceremony"));
  tickCountdown();
  var cdTimer = setInterval(tickCountdown, 1000);

  /* ============================================================ 4. COUPLE */
  function personCard(p, side) {
    var el = document.createElement("article");
    el.className = "person reveal";
    el.innerHTML =
      '<div class="person__frame person__frame--empty"><span class="person__initial">' +
        esc((p.name || "?").charAt(0)) + "</span></div>" +
      '<h3 class="person__name">' + esc(p.fullName || p.name) + "</h3>" +
      '<p class="person__parents">' + esc(p.parents) + "</p>" +
      '<p class="person__about">' + esc(p.about) + "</p>";
    el.setAttribute("data-side", side);

    // swap in the real photo only once we know the file exists
    if (p.photo) {
      var probe = new Image();
      probe.onload = function () {
        var frame = $(".person__frame", el);
        frame.classList.remove("person__frame--empty");
        frame.innerHTML = '<img src="' + esc(p.photo) + '" alt="' + esc(p.fullName || p.name) + '" />';
      };
      probe.src = p.photo;
    }
    return el;
  }
  var coupleGrid = $("#coupleGrid");
  coupleGrid.appendChild(personCard(bride, "bride"));
  coupleGrid.appendChild(personCard(groom, "groom"));

  /* ============================================================= 5. STORY */
  $("#storyLine").innerHTML = (C.story || []).map(function (b) {
    return '<li class="beat reveal">' +
             '<p class="beat__year">' + esc(b.year) + "</p>" +
             '<h3 class="beat__title">' + esc(b.title) + "</h3>" +
             '<p class="beat__text">' + esc(b.text) + "</p>" +
           "</li>";
  }).join("");

  /* ============================================================ 6. EVENTS */
  var ICONS = {
    haldi: "🌼", mehendi: "🌿", sangeet: "🪘", wedding: "🔱",
    reception: "🥂", ring: "💍", generic: "✨"
  };

  function icsStamp(d) {
    return d.getFullYear() + pad(d.getMonth() + 1) + pad(d.getDate()) + "T" +
           pad(d.getHours()) + pad(d.getMinutes()) + "00";
  }
  function icsEscape(s) {
    return String(s || "").replace(/[\;,]/g, "\\$&").replace(/\n/g, "\\n");
  }
  function icsUtcStamp(d) {
    return d.getUTCFullYear() + pad(d.getUTCMonth() + 1) + pad(d.getUTCDate()) + "T" +
           pad(d.getUTCHours()) + pad(d.getUTCMinutes()) + pad(d.getUTCSeconds()) + "Z";
  }
  // RFC 5545 caps a content line at 75 octets; continuations start with a space.
  function icsFold(line) {
    if (line.length <= 74) return line;
    var out = line.slice(0, 74), rest = line.slice(74);
    while (rest.length) { out += "\r\n " + rest.slice(0, 73); rest = rest.slice(73); }
    return out;
  }
  function buildIcs(ev) {
    var start = parseDate(ev.date);
    var end   = parseDate(ev.endDate) || new Date(start.getTime() + 3 * 3600 * 1000);
    var uid   = "evt-" + icsStamp(start) + "-" + Math.abs(hash(ev.name)) + "@wedding.invite";
    return [
      "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Wedding Invitation//EN", "CALSCALE:GREGORIAN",
      "BEGIN:VEVENT",
      "UID:" + uid,
      "DTSTAMP:" + icsUtcStamp(new Date()),
      "DTSTART:" + icsStamp(start),
      "DTEND:"   + icsStamp(end),
      "SUMMARY:" + icsEscape(ev.name + " — " + bride.name + " & " + groom.name),
      "DESCRIPTION:" + icsEscape((ev.tagline || "") + (ev.dressCode ? "\nDress code: " + ev.dressCode : "")),
      "LOCATION:" + icsEscape(ev.venue.name + ", " + ev.venue.address),
      "END:VEVENT", "END:VCALENDAR"
    ].map(icsFold).join("\r\n") + "\r\n";
  }
  function hash(s) {
    var h = 0, i;
    for (i = 0; i < String(s).length; i++) { h = (h << 5) - h + String(s).charCodeAt(i); h |= 0; }
    return h;
  }

  $("#eventsGrid").innerHTML = (C.events || []).map(function (ev, i) {
    var d = parseDate(ev.date), dEnd = parseDate(ev.endDate);
    var time = fmtTime(d) + (dEnd ? " – " + fmtTime(dEnd) : " onwards");
    return '<article class="event reveal" data-ev="' + i + '">' +
             '<div class="event__icon">' + (ICONS[ev.icon] || ICONS.generic) + "</div>" +
             '<h3 class="event__name">' + esc(ev.name) + "</h3>" +
             (ev.tagline ? '<p class="event__tag">' + esc(ev.tagline) + "</p>" : "") +
             '<p class="event__when">' + esc(fmtLong(d)) +
               '<span class="event__time">' + esc(time) + "</span></p>" +
             '<p class="event__where"><span class="event__venue">' + esc(ev.venue.name) + "</span><br />" +
               '<span class="event__addr">' + esc(ev.venue.address) + "</span></p>" +
             (ev.dressCode ? '<p class="event__dress">Dress code · ' + esc(ev.dressCode) + "</p>" : "") +
             '<div class="event__actions">' +
               '<a class="chip" href="' + esc(ev.venue.mapUrl) + '" target="_blank" rel="noopener">' +
                 '<svg><use href="#i-pin"/></svg>Directions</a>' +
               '<button class="chip" type="button" data-cal="' + i + '">' +
                 '<svg><use href="#i-cal"/></svg>Add to calendar</button>' +
             "</div>" +
           "</article>";
  }).join("");

  document.addEventListener("click", function (e) {
    var btn = e.target.closest ? e.target.closest("[data-cal]") : null;
    if (!btn) return;
    var ev = C.events[+btn.getAttribute("data-cal")];
    if (!ev) return;
    var blob = new Blob([buildIcs(ev)], { type: "text/calendar;charset=utf-8" });
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url;
    a.download = ev.name.toLowerCase().replace(/[^a-z0-9]+/g, "-") + ".ics";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(url); }, 1500);
  });

  /* =========================================================== 7. GALLERY */
  var galleryGrid = $("#galleryGrid");
  var lightbox = $("#lightbox"), lbImg = $("#lightboxImg"), lbCap = $("#lightboxCap");

  (C.gallery || []).forEach(function (shot) {
    var cell = document.createElement("button");
    cell.type = "button";
    cell.className = "shot shot--empty reveal";
    cell.innerHTML = "<span>" + esc(shot.caption || "Coming soon") + "</span>";
    cell.disabled = true;
    galleryGrid.appendChild(cell);

    var probe = new Image();
    probe.onload = function () {
      cell.classList.remove("shot--empty");
      cell.disabled = false;
      cell.innerHTML = '<img src="' + esc(shot.src) + '" alt="' + esc(shot.caption || "") + '" loading="lazy" />' +
                       (shot.caption ? '<span class="shot__cap">' + esc(shot.caption) + "</span>" : "");
      cell.addEventListener("click", function () {
        lbImg.src = shot.src;
        lbImg.alt = shot.caption || "";
        txt(lbCap, shot.caption);
        lightbox.hidden = false;
        document.body.style.overflow = "hidden";
      });
    };
    probe.src = shot.src;
  });

  function closeLightbox() {
    lightbox.hidden = true;
    lbImg.src = "";
    document.body.style.overflow = "";
  }
  $("#lightboxClose").addEventListener("click", closeLightbox);
  lightbox.addEventListener("click", function (e) { if (e.target === lightbox) closeLightbox(); });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !lightbox.hidden) closeLightbox(); });

  /* ============================================================ 8. WISHES */
  var WISH_KEY = "wedding-wishes";
  var wishForm = $("#wishForm"), wishWall = $("#wishWall"),
      wishStatus = $("#wishStatus"), wishSubmit = $("#wishSubmit");
  var endpoint = (C.wishes && C.wishes.endpoint || "").trim();

  txt($("#wishesPrompt"), C.wishes.prompt);

  function renderWishes(list) {
    if (!list || !list.length) {
      wishWall.innerHTML = '<p class="wishes__empty">No blessings yet — be the first.</p>';
      return;
    }
    wishWall.innerHTML = list.slice().reverse().map(function (w) {
      return '<article class="wish reveal is-in">' +
               '<p class="wish__text">“' + esc(w.message) + '”</p>' +
               '<p class="wish__by">— ' + esc(w.name) + "</p>" +
             "</article>";
    }).join("");
  }
  function localWishes() {
    try { return JSON.parse(localStorage.getItem(WISH_KEY) || "[]"); }
    catch (err) { return []; }
  }
  function saveLocal(list) {
    try { localStorage.setItem(WISH_KEY, JSON.stringify(list)); } catch (err) { /* private mode */ }
  }

  function loadWishes() {
    if (!endpoint) { renderWishes(localWishes()); return; }
    fetch(endpoint, { method: "GET" })
      .then(function (r) { return r.json(); })
      .then(function (data) {
        var list = Array.isArray(data) ? data : (data && data.wishes) || [];
        renderWishes(list);
      })
      .catch(function () { renderWishes(localWishes()); });
  }
  loadWishes();

  wishForm.addEventListener("submit", function (e) {
    e.preventDefault();
    var name = $("#wishName").value.trim();
    var message = $("#wishText").value.trim();
    wishStatus.removeAttribute("data-state");

    if (!name || !message) {
      wishStatus.setAttribute("data-state", "error");
      txt(wishStatus, "Please add both your name and a message.");
      return;
    }

    var entry = { name: name, message: message, at: new Date().toISOString() };
    wishSubmit.disabled = true;
    txt(wishStatus, "Sending…");

    var done = function () {
      wishForm.reset();
      wishSubmit.disabled = false;
      txt(wishStatus, "Thank you — your blessing has been received. 🌸");
      burstPetals(14);
    };

    if (!endpoint) {
      var list = localWishes();
      list.push(entry);
      saveLocal(list);
      renderWishes(list);
      done();
      return;
    }

    // Sent as a simple form POST so Google Apps Script accepts it without
    // a CORS preflight.
    var body = new URLSearchParams();
    body.set("name", entry.name);
    body.set("message", entry.message);

    fetch(endpoint, { method: "POST", body: body })
      .then(function () { done(); setTimeout(loadWishes, 900); })
      .catch(function () {
        var l = localWishes(); l.push(entry); saveLocal(l); renderWishes(l);
        wishSubmit.disabled = false;
        wishForm.reset();
        txt(wishStatus, "Saved on this device — we'll pick it up shortly. 🌸");
      });
  });

  /* =========================================================== 9. CLOSING */
  txt($("#closingLine"), C.closing.line);
  txt($("#closingSign"), C.closing.signOff);
  txt($("#closingHashtag"), C.couple.hashtag);
  $("#closingContacts").innerHTML = (C.contacts || []).map(function (c) {
    return '<p class="contact"><a href="tel:' + esc(c.phone) + '">' + esc(c.name) + "</a>" +
           "<span>" + esc(c.role) + "</span></p>";
  }).join("");

  /* ============================================================ 10. SHARE */
  $("#btnShare").addEventListener("click", function () {
    var url = (C.share && C.share.url) || window.location.href;
    var text = ((C.share && C.share.message) || "You're invited!") + " " + url;
    window.open("https://wa.me/?text=" + encodeURIComponent(text), "_blank", "noopener");
  });

  /* ============================================================ 11. MUSIC */
  var btnMusic = $("#btnMusic"), audio = null, musicWanted = false;
  if (C.music && C.music.src) {
    audio = new Audio(C.music.src);
    audio.loop = true;
    audio.volume = 0.45;
    audio.addEventListener("canplaythrough", function () {
      btnMusic.hidden = false;
      txt($("#musicTip"), C.music.label || "Music");
      if (musicWanted) play();
    }, { once: true });
    audio.addEventListener("error", function () { btnMusic.hidden = true; audio = null; });
    audio.load();
  }
  function play() {
    if (!audio) return;
    var p = audio.play();
    if (p && p.catch) p.catch(function () { /* autoplay blocked — user can tap */ });
    btnMusic.setAttribute("aria-pressed", "true");
    btnMusic.setAttribute("aria-label", "Pause background music");
  }
  function pause() {
    if (!audio) return;
    audio.pause();
    btnMusic.setAttribute("aria-pressed", "false");
    btnMusic.setAttribute("aria-label", "Play background music");
  }
  btnMusic.addEventListener("click", function () {
    if (!audio) return;
    audio.paused ? play() : pause();
  });

  /* =========================================================== 12. PETALS */
  var petalBox = $("#petals");
  var PETAL_COLORS = ["#E8871E", "#F2B441", "#C9A227", "#F6D27A", "#EBB16A"];

  function makePetal(fromTop) {
    var p = document.createElement("i");
    var size = 6 + Math.random() * 6;
    p.className = "petal";
    p.style.left = Math.random() * 100 + "vw";
    p.style.width = size + "px";
    p.style.height = size * (1.4 + Math.random() * 0.5) + "px";
    p.style.background = PETAL_COLORS[(Math.random() * PETAL_COLORS.length) | 0];
    p.style.setProperty("--dx", (Math.random() * 160 - 80) + "px");
    var dur = 9 + Math.random() * 8;
    p.style.animationDuration = dur + "s";
    p.style.animationDelay = (fromTop ? 0 : Math.random() * 2) + "s";
    petalBox.appendChild(p);
    setTimeout(function () { p.remove(); }, (dur + 3) * 1000);
  }
  function burstPetals(n) {
    if (reduceMotion) return;
    for (var i = 0; i < n; i++) setTimeout(makePetal, i * 90);
  }
  var petalTimer = null;
  function startPetals() {
    if (reduceMotion) return;
    burstPetals(10);
    petalTimer = setInterval(function () {
      if (!document.hidden) makePetal(true);
    }, 2300);
  }
  document.addEventListener("visibilitychange", function () {
    if (document.hidden && petalTimer) { clearInterval(petalTimer); petalTimer = null; }
    else if (!document.hidden && !petalTimer && document.body.classList.contains("is-open")) startPetals();
  });

  /* ====================================================== 13. SCROLL REVEAL */
  var io = ("IntersectionObserver" in window)
    ? new IntersectionObserver(function (entries) {
        entries.forEach(function (en) {
          if (en.isIntersecting) { en.target.classList.add("is-in"); io.unobserve(en.target); }
        });
      }, { rootMargin: "0px 0px -12% 0px", threshold: 0.06 })
    : null;

  function watchReveals() {
    $$(".reveal:not(.is-in)").forEach(function (el) {
      if (io) io.observe(el); else el.classList.add("is-in");
    });
  }

  /* ==================================================== 14. OPEN THE STAGE */
  var stage = $("#stage"), invite = $("#invite"), opener = $("#opener");

  var STYLES = ["doors", "curtains", "envelope", "unfold"];
  var qsStyle = (new URLSearchParams(window.location.search).get("opener") || "").toLowerCase();
  var style = STYLES.indexOf(qsStyle) > -1 ? qsStyle
            : (STYLES.indexOf((C.opener.style || "").toLowerCase()) > -1 ? C.opener.style.toLowerCase() : "doors");
  stage.setAttribute("data-style", style);

  var COVER_NAMES = esc(bride.name) + "<em>and</em>" + esc(groom.name);
  var TAP_HINT = "Tap to open";

  // Scenery goes in front of .opener in the DOM but behind it in z-order,
  // so the shared invitation card is revealed as the scenery parts.
  var SCENERY = {
    doors: function () {
      return '<div class="leaf leaf--l"><div class="leaf__carving"></div></div>' +
             '<div class="leaf leaf--r"><div class="leaf__carving"></div></div>';
    },
    curtains: function () {
      return '<div class="drape drape--l"></div>' +
             '<div class="drape drape--r"></div>' +
             '<div class="valance"></div>';
    },
    envelope: function () {
      return '<div class="env">' +
               '<div class="env__face">' +
                 '<p class="env__names">' + COVER_NAMES + "</p>" +
                 '<p class="env__hint">' + TAP_HINT + "</p>" +
               "</div>" +
               '<div class="env__flap"></div>' +
               '<div class="env__seal">\u0950</div>' +
               '<button class="cover-hit" type="button" data-open aria-label="' + esc(C.opener.buttonLabel || "Open Invitation") + '"></button>' +
             "</div>";
    },
    unfold: function () {
      return '<div class="fold">' +
               '<div class="fold__wing fold__wing--l"></div>' +
               '<div class="fold__wing fold__wing--r"></div>' +
               '<div class="fold__seam"></div>' +
               '<div class="fold__cover">' +
                 '<div class="fold__medallion">\u0950</div>' +
                 '<p class="fold__names">' + COVER_NAMES + "</p>" +
                 '<p class="fold__hint">' + TAP_HINT + "</p>" +
               "</div>" +
               '<button class="cover-hit" type="button" data-open aria-label="' + esc(C.opener.buttonLabel || "Open Invitation") + '"></button>' +
             "</div>";
    }
  };

  var scenery = document.createElement("div");
  scenery.innerHTML = SCENERY[style]();
  while (scenery.firstChild) stage.insertBefore(scenery.firstChild, opener);

  // Styles whose cover hides the invitation card until it opens need a longer
  // beat so the reveal is actually seen before the stage clears.
  var CLEAR_AFTER = (style === "envelope" || style === "unfold") ? 2400 : 1700;

  function openInvitation() {
    if (stage.classList.contains("is-open")) return;
    stage.classList.add("is-open");
    document.body.classList.remove("is-locked");
    document.body.classList.add("is-open");
    invite.setAttribute("aria-hidden", "false");
    musicWanted = true;
    if (audio) play();               // counts as a user gesture, so autoplay is allowed
    startPetals();
    watchReveals();
    setTimeout(function () { stage.classList.add("is-done"); }, CLEAR_AFTER - 700);
    setTimeout(function () {
      stage.style.display = "none";
      window.scrollTo({ top: 0 });
    }, CLEAR_AFTER);
  }

  $("#btnOpen").addEventListener("click", openInvitation);
  $$("[data-open]").forEach(function (el) { el.addEventListener("click", openInvitation); });
})();
