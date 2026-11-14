/* OpenTV — interazioni + i18n IT/EN */
(function () {
  'use strict';

  var doc = document.documentElement;
  doc.classList.add('js');

  /* ---------- Header allo scroll ---------- */
  var header = document.querySelector('.site-header');
  var onScroll = function () {
    header.classList.toggle('scrolled', window.scrollY > 24);
  };
  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  /* ---------- Menu mobile ---------- */
  var toggle = document.querySelector('.nav-toggle');
  var links = document.querySelector('.nav-links');
  if (toggle && links) {
    toggle.addEventListener('click', function () {
      var open = links.classList.toggle('open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });
    links.addEventListener('click', function (e) {
      if (e.target.closest('a')) {
        links.classList.remove('open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
    document.addEventListener('click', function (e) {
      if (!e.target.closest('.nav')) {
        links.classList.remove('open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /* ---------- Reveal on scroll ---------- */
  var reveals = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add('in');
          io.unobserve(en.target);
        }
      });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
    reveals.forEach(function (el) { io.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add('in'); });
  }

  /* ---------- Tilt leggero sul mockup TV ---------- */
  var mock = document.getElementById('mock');
  var tv = mock ? mock.querySelector('.tv') : null;
  var finePointer = window.matchMedia('(pointer: fine)').matches;
  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (mock && tv && finePointer && !reduced) {
    mock.addEventListener('mousemove', function (e) {
      var r = mock.getBoundingClientRect();
      var x = (e.clientX - r.left) / r.width - 0.5;
      var y = (e.clientY - r.top) / r.height - 0.5;
      tv.style.transform = 'rotateY(' + (x * 6).toFixed(2) + 'deg) rotateX(' + (-y * 5).toFixed(2) + 'deg)';
    });
    mock.addEventListener('mouseleave', function () {
      tv.style.transform = '';
    });
  }

  /* ---------- FAQ: una domanda aperta alla volta ---------- */
  var faqItems = document.querySelectorAll('.faq-list details');
  faqItems.forEach(function (d) {
    d.addEventListener('toggle', function () {
      if (!d.open) return;
      faqItems.forEach(function (o) { if (o !== d) o.open = false; });
    });
  });

  /* ---------- i18n IT / EN ---------- */
  var EN = {
    'meta.title': 'OpenTV · Free TV, on every screen',
    'a11y.skip': 'Skip to content',
    'nav.features': 'Features',
    'nav.devices': 'Devices',
    'nav.how': 'How it works',
    'nav.oss': 'Open source',
    'nav.faq': 'FAQ',
    'nav.github': 'GitHub',
    'nav.download': 'Download',
    'hero.badge': 'v1.0.0 — new release · Nov 14, 2026',
    'hero.title': 'Free TV, on <span class="grad">every screen</span>.',
    'hero.sub': "OpenTV is a native, open-source IPTV player for free-to-air channels. Open the app, pick your country and you're watching \u2014 TV guide, favorites and reminders included. Zero setup.",
    'hero.cta.dl': 'Download v1.0.0',
    'hero.cta.gh': 'On GitHub',
    'stats.1': 'supported platforms',
    'stats.2': 'setup required',
    'stats.3': 'offline playlist cache',
    'stats.4': 'open source · MIT license',
    'ticker.label': 'Free-to-air channels ready at launch',
    'features.kicker': 'Features',
    'features.title': 'Everything you need, built in',
    'features.sub': 'No accounts, no configuration: OpenTV takes care of playlists, the TV guide and playback.',
    'f1.t': 'Zero setup',
    'f1.d': "Your country's channels are ready at first launch, with public playlists refreshed automatically.",
    'f2.t': 'Automatic TV guide',
    'f2.d': 'XMLTV EPG loads by itself: now/next, full schedule and a grid guide to plan your evening.',
    'f3.t': 'Built for the living room',
    'f3.d': 'On Android TV: D-pad navigation, TV banner and a remote-friendly player.',
    'f4.t': 'Favorites & history',
    'f4.d': 'Independent favorite lists for every source and recently watched channels pinned to Home.',
    'f5.t': 'Program reminders',
    'f5.d': "Get a system notification when the program you've been waiting for starts.",
    'f6.t': 'Search & genres',
    'f6.d': 'Find channels by name (case- and accent-insensitive) and filter by playlist category.',
    'f7.t': 'Numeric zapping',
    'f7.d': 'Type a channel number to jump straight to it, like the remotes of the old days.',
    'f8.t': 'Robust playback',
    'f8.d': 'HLS, DASH and MPEG-TS with automatic reconnection and a 6-hour offline playlist cache.',
    'f9.t': 'Mini-player & stats',
    'f9.d': 'Picture-in-Picture so you can do other things while watching, plus a VLC-style stream stats panel.',
    'dl.kicker': 'Download',
    'dl.title': 'One app, five destinations',
    'dl.sub': 'Native packages for every platform, published on GitHub Releases.',
    'dl.btn': 'Download',
    'd1.t': 'Android · phones, tablets & TV',
    'd1.d': 'One signed universal APK, including Android TV with leanback launcher.',
    'd1.meta': 'OpenTV-v1.0.0-Android.apk · 4.1 MB',
    'd1.note': 'Works on phones, tablets and Android TV.',
    'd2.t': 'Windows',
    'd2.d': 'MSI and EXE installers with libVLC bundled: no separate VLC installation needed.',
    'd2.meta': 'MSI + EXE · ~201 MB',
    'd2.note': 'libVLC 3.0.24 engine included in the package.',
    'd3.t': 'Linux',
    'd3.d': 'DEB and RPM packages for the main distributions.',
    'd3.meta': 'DEB + RPM · 107\u2013127 MB',
    'd3.note': 'Requires VLC on the system: sudo apt install vlc.',
    'd4.t': 'macOS',
    'd4.d': 'Native DMGs for Apple Silicon and Intel.',
    'd4.meta': 'DMG · Apple Silicon + Intel · 134\u2013135 MB',
    'd4.note': 'Unsigned: right-click \u2192 Open on first run. Requires VLC in /Applications.',
    'dl.foot': 'Signed build for Android; desktop packages generated automatically by GitHub Actions.',
    'dl.all': 'All releases on GitHub',
    'how.kicker': 'How it works',
    'how.title': "Three steps and you're on air",
    'how.sub': 'From installation to your first evening in front of the screen.',
    's1.t': 'Download & install',
    's1.d': 'Pick the package for your device from the Releases page and install it like any regular app.',
    's2.t': 'Pick your country',
    's2.d': 'OpenTV automatically fetches the public iptv-org playlists and organizes free-to-air channels by country.',
    's3.t': 'Sit back and watch',
    's3.d': 'Browse the guide, save favorites, set a reminder and enjoy fullscreen playback.',
    'oss.kicker': 'Open source',
    'oss.title': 'Open source, for real',
    'oss.text': 'The whole project is public on GitHub under the MIT license: Kotlin for Android and desktop, Media3/ExoPlayer as the engine on Android and libVLC on desktop. Issues, pull requests and stars are welcome.',
    'oss.b1': 'Star on GitHub',
    'oss.b2': 'Report a bug',
    'oss.b3': 'Contribute',
    'oss.stack': 'Tech stack',
    'oss.license': 'Free to use, study and modify',
    'faq.kicker': 'FAQ',
    'faq.title': 'Frequently asked questions',
    'q1': 'Is OpenTV free?',
    'a1': "Yes. It's an open-source project released under the MIT license: you can use, study, modify and redistribute it freely.",
    'q2': 'Where do the channels come from?',
    'a2': 'From the public playlists of the iptv-org project, which collects free-to-air channels freely available online. OpenTV downloads and refreshes them automatically, with a 6-hour cache that keeps things working offline.',
    'q3': 'Do I need to configure anything?',
    'a3': 'No. On first launch you pick your country and the channels are already there, TV guide included.',
    'q4': 'Do Linux and macOS need VLC?',
    'a4': 'Yes: the Linux and macOS builds use the system VLC. On Windows, libVLC is already bundled in the MSI and EXE packages.',
    'q5': 'Can I use my own M3U playlist?',
    'a5': 'Yes: OpenTV ships a dedicated M3U/M3U8 parser with local caching and supports playlist and profile import/export.',
    'cta.title': 'Ready to watch?',
    'cta.sub': 'Download OpenTV and switch on all your screens.',
    'cta.btn': 'Download OpenTV',
    'cta.gh': 'View the project',
    'footer.by': 'An open-source project by',
    'footer.license': 'Released under the MIT license',
    'mock.title': 'TV guide · Tonight',
    'mock.chname': 'Canale 01 · Live',
    'mock.chip1': 'Automatic EPG',
    'mock.chip2': 'D-pad ready for the living room',
    'mock.phstatus': 'Live · HD'
  };

  var nodes = Array.prototype.slice.call(document.querySelectorAll('[data-i18n]'));
  var originals = new Map();
  nodes.forEach(function (n) { originals.set(n, n.innerHTML); });

  var langBtn = document.getElementById('langBtn');

  function applyLang(lang) {
    nodes.forEach(function (n) {
      var key = n.getAttribute('data-i18n');
      if (lang === 'en') {
        if (EN[key] != null) n.innerHTML = EN[key];
      } else {
        n.innerHTML = originals.get(n);
      }
    });
    doc.setAttribute('lang', lang);
    if (langBtn) langBtn.textContent = lang === 'it' ? 'EN' : 'IT';
    try { localStorage.setItem('opentv-lang', lang); } catch (err) {}
  }

  var saved = null;
  try { saved = localStorage.getItem('opentv-lang'); } catch (err) {}
  if (saved === 'en') applyLang('en');

  if (langBtn) {
    langBtn.addEventListener('click', function () {
      applyLang(doc.getAttribute('lang') === 'it' ? 'en' : 'it');
    });
  }
})();
