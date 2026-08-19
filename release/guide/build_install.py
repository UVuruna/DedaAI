"""STALE GENERATOR - DO NOT RE-RUN AS-IS (2026-08-20).
docs/install.html has since been edited BY HAND: hero download buttons
(one per language) and the real 17 MB size. Regenerating from this
script would silently revert those. Port the hand edits here first
(tracked as T39), then delete this notice.
"""
# -*- coding: utf-8 -*-
"""Builds the trilingual illustrated INSTALL guide (release/deda-repo/install.html)
— the page a new user lands on after scanning the QR code. Same design system as
the API-key guide (shared guide.css / guide.js). Everything from T down to the
final HTML is user-facing copy in sr/sl/en, wrapped lang-ok."""
import io
import os

CSS = io.open('guide.css', encoding='utf-8').read()
JS = io.open('guide.js', encoding='utf-8').read()
APK = 'https://github.com/UVuruna/DedaAI/releases/latest/download/deda.apk'

# lang-ok-begin: everything below is user-facing guide copy in sr/sl/en
T = {
'sr': {
 'title':'Deda — instalacija','tab':'SR',
 'lede':'Skenirao si QR ili otvorio link — odlično. Prati slike redom i za par minuta imaš Dedu na telefonu. Nije iz prodavnice aplikacija, pa Android par puta pita „da li si siguran“ — to je normalno.',
 'why_h':'Da li je bezbedno?','why_b':'Jeste. Aplikacija se ne krije — Android samo pita jer nije preuzeta sa Play prodavnice. Ti odlučuješ i potvrđuješ svaki korak.',
 'steps':[
  ('1','Preuzmi aplikaciju','Na stranici koja se otvorila klikni zeleno dugme <b>Preuzmi deda.apk</b>. Fajl se skida par sekundi.', 'dl'),
  ('2','Otvori preuzeti fajl','Kad se skine, povuci prstom nadole traku obaveštenja i klikni na <b>deda.apk — Otvori</b>. Ako Chrome zaglavi na „Preuzimanje...“ iako je traka puna — fajl JESTE skinut: otvori aplikaciju <b>Moji fajlovi → Preuzimanja → deda.apk</b> i klikni ga tu.', 'notif'),
  ('3','Dozvoli instalaciju','Android pita da li smeš da instaliraš iz ovog izvora. Uključi prekidač / klikni <b>Dozvoli (Allow / Settings)</b>, pa se vrati.', 'unknown'),
  ('4','Play Protect: Ipak instaliraj','Ako iskoči „nepoznat programer / nebezbedna aplikacija“, to je samo zato što nije sa Play prodavnice. Klikni <b>Ipak instaliraj (Install anyway)</b>.', 'protect'),
  ('5','Samsung: Auto Blocker','Samo na Samsung telefonu, ako instalacija stane: Podešavanja → Bezbednost i privatnost → <b>Auto Blocker</b> → privremeno isključi, instaliraj, pa ga vrati.', 'blocker'),
  ('6','Instaliraj i otvori','Klikni <b>Instaliraj (Install)</b>, sačekaj, pa <b>Otvori (Open)</b>.', 'install'),
  ('7','Dozvole','Pri prvom pokretanju odobri <b>mikrofon</b> i <b>obaveštenja</b> — bez njih Deda ne može da sluša ni da javi da radi.', 'perm'),
 ],
 'done_h':'Instalirano! Ostaje ključ.','done_b':'Deda još treba tvoj besplatan Google ključ (dva minuta). Otvori vodič: <a href="vodic.html"><b>Kako napraviti ključ →</b></a>',
 'callouts':{'dl':'Klikni <b>Preuzmi deda.apk</b>.','notif':'Klikni <b>Otvori</b>.',
   'unknown':'Uključi prekidač → nazad.','protect':'Klikni <b>Install anyway</b>.',
   'blocker':'Isključi <b>Auto Blocker</b> privremeno.','install':'Klikni <b>Install</b>, pa <b>Open</b>.',
   'perm':'Klikni <b>Allow / Dozvoli</b>.',
   'dlbtn':'Preuzmi deda.apk','ntt':'deda.apk','nsub':'Preuzimanje završeno','nact':'Otvori',
   'unk_t':'Instaliraj nepoznate aplikacije','unk_b':'Dozvoli ovom pregledaču da instalira aplikacije.',
   'pp_t':'Nebezbedna aplikacija?','pp_b':'Play Protect ne prepoznaje programera. Možeš svejedno da instaliraš.',
   'pp_no':'Ne instaliraj','pp_yes':'Ipak instaliraj','inst':'Instaliraj','open':'Otvori',
   'ab_t':'Auto Blocker','ab_s':'Uključeno','sec':'Bezbednost i privatnost','perm_t':'Dozvoli aplikaciji Deda da koristi mikrofon?','allow':'Dozvoli','deny':'Ne dozvoli'},
},
'sl': {
 'title':'Deda — namestitev','tab':'SL',
 'lede':'Skeniral si QR ali odprl povezavo — odlično. Sledi slikam po vrsti in v nekaj minutah imaš Dedo na telefonu. Ni iz trgovine, zato Android nekajkrat vpraša „ali si prepričan“ — to je normalno.',
 'why_h':'Je varno?','why_b':'Je. Aplikacija se ne skriva — Android samo vpraša, ker ni prenesena iz trgovine Play. Ti odločaš in potrjuješ vsak korak.',
 'steps':[
  ('1','Prenesi aplikacijo','Na strani, ki se je odprla, klikni zeleni gumb <b>Prenesi deda.apk</b>. Datoteka se prenese v nekaj sekundah.', 'dl'),
  ('2','Odpri preneseno datoteko','Ko se prenese, povleci navzdol obvestilno vrstico in klikni <b>deda.apk — Odpri</b>. Če Chrome obtiči na „Prenašanje...“, čeprav je vrstica polna — datoteka JE prenesena: odpri <b>Moje datoteke → Prenosi → deda.apk</b> in jo klikni tam.', 'notif'),
  ('3','Dovoli namestitev','Android vpraša, ali smeš namestiti iz tega vira. Vklopi stikalo / klikni <b>Dovoli (Allow / Settings)</b> in se vrni.', 'unknown'),
  ('4','Play Protect: Vseeno namesti','Če se pojavi „neznan razvijalec / nevarna aplikacija“, je to le zato, ker ni iz trgovine Play. Klikni <b>Vseeno namesti (Install anyway)</b>.', 'protect'),
  ('5','Samsung: Auto Blocker','Samo na Samsung telefonu, če se namestitev ustavi: Nastavitve → Varnost in zasebnost → <b>Auto Blocker</b> → začasno izklopi, namesti, nato vklopi nazaj.', 'blocker'),
  ('6','Namesti in odpri','Klikni <b>Namesti (Install)</b>, počakaj, nato <b>Odpri (Open)</b>.', 'install'),
  ('7','Dovoljenja','Ob prvem zagonu odobri <b>mikrofon</b> in <b>obvestila</b> — brez njih Deda ne more poslušati ali sporočati.', 'perm'),
 ],
 'done_h':'Nameščeno! Ostane ključ.','done_b':'Deda še potrebuje tvoj brezplačni Googlov ključ (dve minuti). Odpri vodič: <a href="vodic.html"><b>Kako narediti ključ →</b></a>',
 'callouts':{'dl':'Klikni <b>Prenesi deda.apk</b>.','notif':'Klikni <b>Odpri</b>.',
   'unknown':'Vklopi stikalo → nazaj.','protect':'Klikni <b>Install anyway</b>.',
   'blocker':'Začasno izklopi <b>Auto Blocker</b>.','install':'Klikni <b>Install</b>, nato <b>Open</b>.',
   'perm':'Klikni <b>Allow / Dovoli</b>.',
   'dlbtn':'Prenesi deda.apk','ntt':'deda.apk','nsub':'Prenos končan','nact':'Odpri',
   'unk_t':'Namesti neznane aplikacije','unk_b':'Dovoli temu brskalniku nameščanje aplikacij.',
   'pp_t':'Nevarna aplikacija?','pp_b':'Play Protect ne prepozna razvijalca. Vseeno lahko namestiš.',
   'pp_no':'Ne namesti','pp_yes':'Vseeno namesti','inst':'Namesti','open':'Odpri',
   'ab_t':'Auto Blocker','ab_s':'Vklopljeno','sec':'Varnost in zasebnost','perm_t':'Dovoli aplikaciji Deda uporabo mikrofona?','allow':'Dovoli','deny':'Ne dovoli'},
},
'en': {
 'title':'Deda — install','tab':'EN',
 'lede':'You scanned the QR or opened the link — great. Follow the pictures in order and in a couple of minutes Deda is on your phone. It is not from the app store, so Android asks “are you sure” a few times — that is normal.',
 'why_h':'Is it safe?','why_b':'Yes. The app is not hiding — Android just asks because it did not come from the Play Store. You decide and confirm each step.',
 'steps':[
  ('1','Download the app','On the page that opened, click the green <b>Download deda.apk</b> button. The file downloads in a few seconds.', 'dl'),
  ('2','Open the downloaded file','When it finishes, pull down the notification bar and tap <b>deda.apk — Open</b>. If Chrome sticks on “Downloading...” even with a full bar — the file IS downloaded: open <b>My Files → Downloads → deda.apk</b> and tap it there.', 'notif'),
  ('3','Allow the install','Android asks if you may install from this source. Turn on the switch / tap <b>Allow (Settings)</b>, then go back.', 'unknown'),
  ('4','Play Protect: Install anyway','If “unknown developer / unsafe app” appears, that is only because it is not from the Play Store. Tap <b>Install anyway</b>.', 'protect'),
  ('5','Samsung: Auto Blocker','Samsung phones only, if the install stalls: Settings → Security and privacy → <b>Auto Blocker</b> → turn off temporarily, install, then turn it back on.', 'blocker'),
  ('6','Install and open','Tap <b>Install</b>, wait, then <b>Open</b>.', 'install'),
  ('7','Permissions','On first launch grant <b>microphone</b> and <b>notifications</b> — without them Deda cannot listen or tell you it is on.', 'perm'),
 ],
 'done_h':'Installed! Now the key.','done_b':'Deda still needs your free Google key (two minutes). Open the guide: <a href="vodic.html"><b>How to make a key →</b></a>',
 'callouts':{'dl':'Click <b>Download deda.apk</b>.','notif':'Tap <b>Open</b>.',
   'unknown':'Turn on the switch → back.','protect':'Tap <b>Install anyway</b>.',
   'blocker':'Turn <b>Auto Blocker</b> off temporarily.','install':'Tap <b>Install</b>, then <b>Open</b>.',
   'perm':'Tap <b>Allow</b>.',
   'dlbtn':'Download deda.apk','ntt':'deda.apk','nsub':'Download complete','nact':'Open',
   'unk_t':'Install unknown apps','unk_b':'Allow this browser to install apps.',
   'pp_t':'Unsafe app?','pp_b':'Play Protect does not recognise the developer. You can install anyway.',
   'pp_no':'Don’t install','pp_yes':'Install anyway','inst':'Install','open':'Open',
   'ab_t':'Auto Blocker','ab_s':'On','sec':'Security and privacy','perm_t':'Allow Deda to use the microphone?','allow':'Allow','deny':'Deny'},
},
}

def phone(inner):
    return '<div class="mock phone"><div class="pscreen">' + inner + '</div></div>'

def mock(name, c):
    if name == 'dl':
        return ('<div class="mock browser"><div class="bbar"><span class="d r"></span><span class="d y"></span>'
          '<span class="d g"></span><div class="url">github.com/UVuruna/DedaAI</div></div>'
          '<div class="mpage ctr"><div class="gt">Deda 👓</div>'
          '<div class="dlbtn">⬇ ' + c['dlbtn'] + '</div>'
          '<div class="call" style="position:static;margin-top:6px">' + c['dl'] + '</div></div></div>')
    if name == 'notif':
        return phone('<div class="notif"><div class="nico">▼</div><div class="ntx"><div class="ntt">' + c['ntt'] + '</div>'
          '<div class="nsub">' + c['nsub'] + '</div></div><div class="nact">' + c['nact'] + '</div></div>'
          '<div class="call" style="top:70px;left:120px">' + c['notif'] + '</div>')
    if name == 'unknown':
        return phone('<div style="margin:auto 0"><div class="tgl"><span>' + c['unk_t'] + '</span><span class="sw"></span></div>'
          '<div class="adb" style="color:#8b949e;margin-top:8px">' + c['unk_b'] + '</div></div>'
          '<div class="call" style="top:60px;left:150px">' + c['unknown'] + '</div>')
    if name == 'protect':
        return phone('<div class="adlg"><div class="adt">⚠ ' + c['pp_t'] + '</div><div class="adb">' + c['pp_b'] + '</div>'
          '<div class="adrow"><button class="adbtn">' + c['pp_no'] + '</button><button class="adbtn strong">' + c['pp_yes'] + '</button></div></div>'
          '<div class="call" style="top:150px;left:120px">' + c['protect'] + '</div>')
    if name == 'blocker':
        return phone('<div class="psh" style="margin-top:4px">' + c['sec'] + '</div>'
          '<div class="sett"><div class="setrow"><span>' + c['ab_t'] + '</span><span class="sw"></span></div>'
          '<div class="setrow"><span class="mut">' + c['ab_s'] + '</span></div></div>'
          '<div class="call" style="top:48px;left:120px">' + c['blocker'] + '</div>')
    if name == 'install':
        return phone('<div class="adlg"><div class="adt">Deda 👓</div>'
          '<div class="adb">Ray-Ban Meta • 17 MB</div>'
          '<div class="adrow"><button class="adbtn">' + c['pp_no'] + '</button><button class="adbtn strong">' + c['inst'] + '</button></div></div>'
          '<div class="call" style="top:120px;left:120px">' + c['install'] + '</div>')
    if name == 'perm':
        return phone('<div class="perm"><div class="permico">🎤</div><div class="permt">' + c['perm_t'] + '</div>'
          '<div class="permrow"><button class="permb deny">' + c['deny'] + '</button><button class="permb allow">' + c['allow'] + '</button></div></div>'
          '<div class="call" style="top:180px;left:120px">' + c['perm'] + '</div>')
    return ''

def section(code):
    t = T[code]; c = t['callouts']
    steps = ''
    for num, title, body, m in t['steps']:
        steps += ('<article class="step"><div class="txt"><div class="num">' + num + '</div>'
                  '<h3>' + title + '</h3><p>' + body + '</p></div>'
                  '<div class="pic">' + mock(m, c) + '</div></article>')
    return ('<section class="lang" data-lang="' + code + '"><h1>' + t['title'] + '</h1>'
            '<p class="lede">' + t['lede'] + '</p>'
            '<div class="why"><h2>' + t['why_h'] + '</h2><p>' + t['why_b'] + '</p></div>' + steps +
            '<div class="done"><h2>' + t['done_h'] + '</h2><p>' + t['done_b'] + '</p></div></section>')

TABS = ''.join('<button class="tab" data-go="' + k + '">' + T[k]['tab'] + '</button>' for k in ('sr', 'sl', 'en'))
SECTIONS = ''.join(section(k) for k in ('sr', 'sl', 'en'))

HTML = ('<!doctype html><html lang="sr"><head><meta charset="utf-8">'
 '<meta name="viewport" content="width=device-width,initial-scale=1">'
 '<title>Deda — instalacija</title><style>' + CSS + '</style></head><body>'
 '<header><div class="brand">Deda <span>\U0001F453</span></div>'
 '<nav class="tabs">' + TABS + '</nav></header><main>' + SECTIONS + '</main>'
 '<footer>github.com/UVuruna/DedaAI</footer>'
 '<script>' + JS + '</script></body></html>')
# lang-ok-end

out = os.path.join('..', 'deda-repo', 'install.html')
io.open(out, 'w', encoding='utf-8').write(HTML)
print('wrote', out, len(HTML), 'bytes')
