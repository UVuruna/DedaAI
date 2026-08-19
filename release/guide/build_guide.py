# -*- coding: utf-8 -*-
"""Assembles the trilingual illustrated API-key guide into one self-contained
HTML file (release/deda-repo/vodic.html). Everything from the T table down to
the final HTML string is user-facing guide copy in sr/sl/en, so the whole
region is wrapped lang-ok."""
import io
import os

SIGN = open('_signin_b64.txt', encoding='utf-8').read().strip()
CSS = io.open('guide.css', encoding='utf-8').read()
JS = io.open('guide.js', encoding='utf-8').read()

# lang-ok-begin: everything below is user-facing guide copy in sr/sl/en
T = {
'sr': {
 'title':'Deda — tvoj besplatan ključ','tab':'SR',
 'lede':'Da bi Deda razgovarao, potreban mu je tvoj lični Google ključ. Besplatan je, ne traži karticu, i pravi se za dva minuta. Idi redom kroz slike.',
 'why_h':'Zašto svoj ključ?','why_b':'Svako koristi svoj ključ — tako niko ne troši tuđu besplatnu kvotu i Deda uvek radi. Ključ je kao lozinka: čuvaj ga i ne deli ga.',
 'steps':[
  ('1','Otvori stranicu','U pregledaču (na telefonu ili računaru) ukucaj adresu <b>aistudio.google.com/apikey</b> i pritisni enter.', 'm01'),
  ('2','Prijavi se na Google','Ako te pita, prijavi se svojim Google (Gmail) nalogom: ukucaj mejl pa <b>Next</b>, zatim lozinku. Ako si već prijavljen, ovaj korak preskačeš.', 'signin'),
  ('3','Prihvati uslove','Ako se pojave uslovi korišćenja, štikliraj kvadratiće i klikni <b>Continue</b>. (Ne pojave se svima.)', 'm03'),
  ('4','Napravi ključ','Klikni plavo dugme <b>Create API key</b>.', 'm04'),
  ('5','Potvrdi','Klikni <b>Create API key in new project</b>. Ako pita za projekat, ostavi ono što je predloženo.', 'm05'),
  ('6','Kopiraj ključ','Pojaviće se tvoj ključ (počinje sa <b>AIza…</b>). Klikni <b>Copy</b> da ga kopiraš.', 'm06'),
  ('7','Nalepi u Dedu','Otvori aplikaciju Deda, gore desno zupčanik (Settings). U polje <b>API Key</b> dugo pritisni i izaberi <b>Paste / Nalepi</b>. Izadji nazad (strelicom ili prevlačenjem) — ključ je sačuvan.', 'm07'),
 ],
 'done_h':'Gotovo!','done_b':'Reci <b>„Hej Deda“</b> i pitaj ga nešto. Ako kaže da nema ključ, vrati se na korak 7 i proveri da si nalepio ceo ključ.',
 'callouts':{'c01a':'Ukucaj adresu u pregledač:','c01b':'Radi na telefonu i na računaru — u bilo kom pregledaču.',
   'c03':'Štikliraj kvadratiće pa <b>Continue</b>.','c04':'Klikni plavo dugme <b>Create API key</b>.',
   'c05':'Klikni <b>Create API key in new project</b>.','c06':'Klikni <b>Copy</b> — ključ je kopiran.',
   'empty':'Još nemaš nijedan ključ.','note':'Sačuvaj ovaj ključ. Ne deli ga sa drugima.',
   'c07a':'Dugo pritisni polje → <b>Paste / Nalepi</b>.','c07b':'Nazad (‹ ili prevlačenjem) čuva unos.',
   'hint':'Prazno = ugrađeni podrazumevani ključ. Nalepi svoj besplatni ključ.','keys':'API keys'},
},
'sl': {
 'title':'Deda — tvoj brezplačni ključ','tab':'SL',
 'lede':'Da bi Deda govoril, potrebuje tvoj osebni Googlov ključ. Brezplačen je, ne zahteva kartice in nastane v dveh minutah. Sledi slikam po vrsti.',
 'why_h':'Zakaj svoj ključ?','why_b':'Vsak uporablja svoj ključ — tako nihče ne porablja tuje brezplačne kvote in Deda vedno deluje. Ključ je kot geslo: shrani ga in ga ne deli.',
 'steps':[
  ('1','Odpri stran','V brskalniku (na telefonu ali računalniku) vtipkaj naslov <b>aistudio.google.com/apikey</b> in pritisni enter.', 'm01'),
  ('2','Prijavi se v Google','Če te vpraša, se prijavi s svojim Google (Gmail) računom: vpiši e-pošto in <b>Next</b>, nato geslo. Če si že prijavljen, ta korak preskočiš.', 'signin'),
  ('3','Sprejmi pogoje','Če se pojavijo pogoji uporabe, obkljukaj kvadratke in klikni <b>Continue</b>. (Ne pojavijo se vsem.)', 'm03'),
  ('4','Ustvari ključ','Klikni modri gumb <b>Create API key</b>.', 'm04'),
  ('5','Potrdi','Klikni <b>Create API key in new project</b>. Če vpraša za projekt, pusti predlaganega.', 'm05'),
  ('6','Kopiraj ključ','Pojavil se bo tvoj ključ (začne se z <b>AIza…</b>). Klikni <b>Copy</b>, da ga kopiraš.', 'm06'),
  ('7','Prilepi v Dedo','Odpri aplikacijo Deda, zgoraj desno zobnik (Settings). V polje <b>API Key</b> pritisni in drži ter izberi <b>Paste / Prilepi</b>. Pojdi nazaj (s puščico ali potegom) — ključ je shranjen.', 'm07'),
 ],
 'done_h':'Končano!','done_b':'Reci <b>„Hej Deda“</b> in ga nekaj vprašaj. Če pravi, da nima ključa, se vrni na korak 7 in preveri, da si prilepil cel ključ.',
 'callouts':{'c01a':'Vtipkaj naslov v brskalnik:','c01b':'Deluje na telefonu in na računalniku — v katerem koli brskalniku.',
   'c03':'Obkljukaj kvadratke in <b>Continue</b>.','c04':'Klikni modri gumb <b>Create API key</b>.',
   'c05':'Klikni <b>Create API key in new project</b>.','c06':'Klikni <b>Copy</b> — ključ je kopiran.',
   'empty':'Nimaš še nobenega ključa.','note':'Shrani ta ključ. Ne deli ga z drugimi.',
   'c07a':'Pritisni in drži polje → <b>Paste / Prilepi</b>.','c07b':'Nazaj (‹ ali s potegom) shrani vnos.',
   'hint':'Prazno = vgrajeni privzeti ključ. Prilepi svoj brezplačni ključ.','keys':'API keys'},
},
'en': {
 'title':'Deda — your free key','tab':'EN',
 'lede':'For Deda to talk, it needs your own Google key. It is free, needs no credit card, and takes two minutes. Just follow the pictures in order.',
 'why_h':'Why your own key?','why_b':'Everyone uses their own key — so no one drains anyone else’s free quota and Deda always works. The key is like a password: keep it, don’t share it.',
 'steps':[
  ('1','Open the page','In a browser (phone or computer) type the address <b>aistudio.google.com/apikey</b> and press enter.', 'm01'),
  ('2','Sign in to Google','If asked, sign in with your Google (Gmail) account: type your email then <b>Next</b>, then your password. If already signed in, skip this step.', 'signin'),
  ('3','Accept the terms','If terms of service appear, tick the boxes and click <b>Continue</b>. (Not shown to everyone.)', 'm03'),
  ('4','Create the key','Click the blue <b>Create API key</b> button.', 'm04'),
  ('5','Confirm','Click <b>Create API key in new project</b>. If it asks for a project, keep the suggested one.', 'm05'),
  ('6','Copy the key','Your key appears (it starts with <b>AIza…</b>). Click <b>Copy</b> to copy it.', 'm06'),
  ('7','Paste into Deda','Open the Deda app, tap the gear (Settings) top-right. Long-press the <b>API Key</b> field and choose <b>Paste</b>. Go back (arrow or swipe) — the key is saved.', 'm07'),
 ],
 'done_h':'Done!','done_b':'Say <b>“Hej Deda”</b> and ask it something. If it says it has no key, go back to step 7 and check you pasted the whole key.',
 'callouts':{'c01a':'Type the address in a browser:','c01b':'Works on phone and computer — in any browser.',
   'c03':'Tick the boxes then <b>Continue</b>.','c04':'Click the blue <b>Create API key</b> button.',
   'c05':'Click <b>Create API key in new project</b>.','c06':'Click <b>Copy</b> — the key is copied.',
   'empty':'You have no keys yet.','note':'Save this key. Do not share it.',
   'c07a':'Long-press the field → <b>Paste</b>.','c07b':'Back (‹ or swipe) saves your entry.',
   'hint':'Empty = built-in default key. Paste your own free key.','keys':'API keys'},
},
}

DOTS = '•' * 20

def browser(inner):
    return ('<div class="mock browser"><div class="bbar"><span class="d r"></span>'
            '<span class="d y"></span><span class="d g"></span><div class="url">aistudio.google.com/apikey</div></div>'
            '<div class="mpage">' + inner + '</div></div>')

def mock(name, c):
    if name == 'm01':
        return ('<div class="mock browser"><div class="bbar"><span class="d r"></span><span class="d y"></span>'
          '<span class="d g"></span><div class="url"><b>aistudio.google.com/apikey</b></div></div>'
          '<div class="mpage ctr"><div class="mbig">' + c['c01a'] + '</div><div class="typed">aistudio.google.com/apikey</div>'
          '<div class="mcap">' + c['c01b'] + '</div></div></div>')
    if name == 'signin':
        return '<img class="mock shot" alt="Google sign in" src="data:image/png;base64,' + SIGN + '">'
    if name == 'm03':
        return browser('<div class="gt">Google AI Studio</div><div class="modal"><div class="mh">Terms of Service</div>'
          '<div class="mr"><span class="ck">✔</span> I accept the Google APIs Terms of Service.</div>'
          '<div class="mr"><span class="ck">✔</span> I agree to the additional terms.</div>'
          '<button class="gb">Continue</button><div class="call" style="top:120px">' + c['c03'] + '</div></div>')
    if name == 'm04':
        return browser('<div class="gt">Google AI Studio</div><div class="sub">' + c['keys'] + '</div>'
          '<div class="emp">' + c['empty'] + '</div><button class="gb blue big">＋ Create API key</button>'
          '<div class="call" style="top:150px;left:250px">' + c['c04'] + '</div>')
    if name == 'm05':
        return browser('<div class="gt">Google AI Studio</div><div class="modal"><div class="mh">Create API key</div>'
          '<div class="mr2">Search Google Cloud projects</div><div class="proj">My first project ▾</div>'
          '<button class="gb blue">Create API key in new project</button>'
          '<div class="call" style="top:152px">' + c['c05'] + '</div></div>')
    if name == 'm06':
        return browser('<div class="gt">Google AI Studio</div><div class="modal"><div class="mh">API key generated</div>'
          '<div class="krow"><span class="ktx">AIzaSy' + DOTS + '</span>'
          '<button class="cp">⧉ Copy</button></div><div class="mn">' + c['note'] + '</div>'
          '<div class="call" style="top:96px;left:250px">' + c['c06'] + '</div></div>')
    if name == 'm07':
        return ('<div class="mock phone"><div class="pbar"><span class="par">‹</span> Deda • Settings</div>'
          '<div class="pp"><div class="psh">Gemini API</div><div class="plab">API Key</div>'
          '<div class="pbox glow">AIzaSy' + DOTS + '<span class="car">|</span></div>'
          '<div class="phint">' + c['hint'] + '</div><div class="psh">Language / Jezik</div>'
          '<div class="chips"><span class="chip on">Srpski</span><span class="chip">Slovenščina</span><span class="chip">English</span></div>'
          '<div class="call" style="top:120px;left:150px">' + c['c07a'] + '</div>'
          '<div class="call" style="top:20px;left:205px">' + c['c07b'] + '</div></div></div>')
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
 '<title>Deda — tvoj besplatan ključ</title><style>' + CSS + '</style></head><body>'
 '<header><div class="brand">Deda <span>\U0001F453</span></div>'
 '<nav class="tabs">' + TABS + '</nav></header><main>' + SECTIONS + '</main>'
 '<footer>aistudio.google.com/apikey &middot; github.com/UVuruna/DedaAI</footer>'
 '<script>' + JS + '</script></body></html>')
# lang-ok-end

out = os.path.join('..', 'deda-repo', 'vodic.html')
io.open(out, 'w', encoding='utf-8').write(HTML)
print('wrote', out, len(HTML), 'bytes')
