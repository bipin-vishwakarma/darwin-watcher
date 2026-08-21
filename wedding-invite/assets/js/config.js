/* ==========================================================================
   WEDDING CONFIG  —  EDIT ONLY THIS FILE
   --------------------------------------------------------------------------
   Everything the invitation shows comes from here. Replace the sample values
   with the real ones. Nothing else in the project needs to be touched.
   ========================================================================== */

const WEDDING = {

  /* ---------------------------------------------------------------- couple */
  couple: {
    groom: {
      name: "Aarav",
      fullName: "Aarav Sharma",
      parents: "Son of Mr. Rajesh Sharma & Mrs. Sunita Sharma",
      photo: "assets/img/groom.jpg",           // leave as-is if no photo yet
      about: "An architect who sketches buildings on napkins, makes the world's most over-engineered filter coffee, and laughs at his own jokes first."
    },
    bride: {
      name: "Ananya",
      fullName: "Ananya Verma",
      parents: "Daughter of Mr. Suresh Verma & Mrs. Meera Verma",
      photo: "assets/img/bride.jpg",
      about: "A paediatrician with a bookshelf that has run out of shelf, an incurable weakness for street chaat, and a playlist for every mood."
    },
    // Shown in the title bar / WhatsApp preview
    hashtag: "#AaravWedsAnanya"
  },

  /* ------------------------------------------------------- the main wedding
     Used for the countdown timer. Format: "YYYY-MM-DDTHH:MM:SS" (24-hour,
     local time of the venue).                                                */
  weddingDate: "2026-12-06T19:30:00",

  /* -------------------------------------------------------- opening screen */
  opener: {
    invitationLine: "Together with our families",
    subLine: "we joyfully invite you to celebrate our wedding",
    buttonLabel: "Open Invitation"
  },

  /* ---------------------------------------------------------- the love story
     Add or remove entries freely — the timeline builds itself.              */
  story: [
    { year: "2019", title: "The first meeting",
      text: "A friend's terrace party in Pune. He spilled chai on her dupatta. She has never let him forget it." },
    { year: "2021", title: "The first trip",
      text: "Three days in Coorg, one broken umbrella and a shared decision that this was going to be a long, long story." },
    { year: "2024", title: "The proposal",
      text: "Same terrace. Same chai. This time he had a ring in his pocket and a much steadier hand." },
    { year: "2026", title: "The wedding",
      text: "And now, with the blessings of our families, we ask you to be there when we say forever." }
  ],

  /* -------------------------------------------------------------- the events
     icon: haldi | mehendi | sangeet | wedding | reception | ring | generic
     date: "YYYY-MM-DDTHH:MM:SS"  ·  endDate is optional (for Add-to-Calendar) */
  events: [
    {
      icon: "mehendi",
      name: "Mehendi",
      tagline: "Green leaves, deep colour, louder laughter",
      date: "2026-12-04T16:00:00",
      endDate: "2026-12-04T21:00:00",
      dressCode: "Mint & Marigold",
      venue: {
        name: "Verma Residence Lawns",
        address: "12, Sahakar Nagar, Pune, Maharashtra 411009",
        mapUrl: "https://maps.google.com/?q=Sahakar+Nagar+Pune"
      }
    },
    {
      icon: "haldi",
      name: "Haldi",
      tagline: "Turmeric, sunshine and absolutely no escaping it",
      date: "2026-12-05T10:00:00",
      endDate: "2026-12-05T13:00:00",
      dressCode: "Yellow, and clothes you don't love",
      venue: {
        name: "Verma Residence Courtyard",
        address: "12, Sahakar Nagar, Pune, Maharashtra 411009",
        mapUrl: "https://maps.google.com/?q=Sahakar+Nagar+Pune"
      }
    },
    {
      icon: "sangeet",
      name: "Sangeet",
      tagline: "The night the uncles out-dance everyone",
      date: "2026-12-05T19:00:00",
      endDate: "2026-12-06T00:00:00",
      dressCode: "Indo-western festive",
      venue: {
        name: "The Grand Durbar Hall, Hotel Regalia",
        address: "Airport Road, Viman Nagar, Pune, Maharashtra 411014",
        mapUrl: "https://maps.google.com/?q=Viman+Nagar+Pune"
      }
    },
    {
      icon: "wedding",
      name: "Wedding Ceremony",
      tagline: "Seven steps, seven vows, one lifetime",
      date: "2026-12-06T19:30:00",
      endDate: "2026-12-06T23:00:00",
      dressCode: "Traditional Indian",
      venue: {
        name: "Shree Ganesh Mangal Karyalaya",
        address: "Karve Road, Kothrud, Pune, Maharashtra 411038",
        mapUrl: "https://maps.google.com/?q=Karve+Road+Kothrud+Pune"
      }
    },
    {
      icon: "reception",
      name: "Reception",
      tagline: "Come hungry, leave happy",
      date: "2026-12-07T19:00:00",
      endDate: "2026-12-07T23:30:00",
      dressCode: "Formal / Cocktail",
      venue: {
        name: "Emerald Ballroom, Hotel Regalia",
        address: "Airport Road, Viman Nagar, Pune, Maharashtra 411014",
        mapUrl: "https://maps.google.com/?q=Viman+Nagar+Pune"
      }
    }
  ],

  /* ------------------------------------------------------------- the gallery
     Drop images into assets/img/ and list them here. Any entry whose file is
     missing quietly falls back to a decorative panel, so you can ship the site
     before the photos arrive.                                                */
  gallery: [
    { src: "assets/img/gallery-1.jpg", caption: "Coorg, 2021" },
    { src: "assets/img/gallery-2.jpg", caption: "The proposal" },
    { src: "assets/img/gallery-3.jpg", caption: "Roka ceremony" },
    { src: "assets/img/gallery-4.jpg", caption: "Engagement" },
    { src: "assets/img/gallery-5.jpg", caption: "Family, Diwali 2025" },
    { src: "assets/img/gallery-6.jpg", caption: "Just us" }
  ],

  /* --------------------------------------------------------------- contacts
     Shown at the bottom so guests know whom to call. */
  contacts: [
    { name: "Rohan Sharma",  role: "Groom's brother", phone: "+919812345678" },
    { name: "Priya Verma",   role: "Bride's sister",  phone: "+919887654321" }
  ],

  /* ----------------------------------------------------------------- music
     Put an .mp3 at this path to enable the background music button. If the
     file is absent the button hides itself — nothing breaks.                */
  music: {
    src: "assets/audio/theme.mp3",
    label: "Shehnai"
  },

  /* ----------------------------------------------------------- wishes wall
     endpoint: paste the Google Apps Script Web App URL here (see README →
     "Wishes wall"). Leave it empty ("") and the wall runs in local demo mode:
     wishes are stored in that guest's own browser only.                     */
  wishes: {
    endpoint: "",
    prompt: "Leave a blessing for the couple"
  },

  /* ------------------------------------------------------------------ share
     The public URL of this site, used by the WhatsApp share button.
     Leave "" to use whatever address the page is currently open at.         */
  share: {
    url: "",
    message: "Aarav & Ananya are getting married! 💍 Here is our invitation:"
  },

  /* ------------------------------------------------------------- closing line */
  closing: {
    line: "Your presence is the only gift we ask for.",
    signOff: "With love, the Sharma & Verma families"
  }
};
