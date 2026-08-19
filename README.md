# Deda 👓

<!-- lang-ok: user-facing install guide, the users are Serbian -->
Glasovni asistent za Ray-Ban Meta naočare (Gen 1/2) + Android telefon.
Radi i **bez naočara** — dugme „Start on Phone" koristi kameru i mikrofon
telefona, pa prvo tako proveri da ključ i jezik rade, a naočare dodaj posle.

## Instalacija

**Najnovija verzija (uvek ista adresa):**

### ➡️ [PREUZMI deda.apk](https://github.com/UVuruna/deda/releases/latest/download/deda.apk) ⬅️

ili skeniraj telefonom:

![QR kod za preuzimanje](qr.png)

1. Otvori link (ili skeniraj QR) na telefonu i sačekaj da se `deda.apk` preuzme.
2. Otvori preuzeti fajl i potvrdi instalaciju:
   - Android pita za dozvolu instaliranja nepoznatih aplikacija — dozvoli za
     pregledač koji je skinuo fajl.
   - Ako **Play Protect** upozori („nepoznat programer / nebezbedna
     aplikacija"), izaberi **Install anyway / Ipak instaliraj**.
   - Na **Samsung** telefonu, ako instalacija ćuti ili je odbijena: Podešavanja
     → Bezbednost i privatnost → **Auto Blocker** → privremeno isključi, pa
     instaliraj i vrati ga.
3. Pri prvom pokretanju odobri tražene dozvole (mikrofon, obaveštenja).
4. U Settings (zupčanik gore desno) nalepi **svoj** Gemini API ključ —
   besplatan na [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
   (prijava Google nalogom, bez kartice) — i izaberi jezik. PLACEHOLDER
   nazad u vrhu ekrana** da se sačuva.
5. Proveri bez naočara: na početnom ekranu **Start on Phone** i postavi
   pitanje naglas — ako odgovori, ključ i jezik rade.
6. Za naočare: u **Meta AI** aplikaciji uključi **Developer Mode** — na
   novijim verzijama: Settings → App Info → tapni **broj verzije 5 puta** →
   pojavi se Developer Mode prekidač; na starijim je pod Privacy. Naočare
   moraju već biti uparene sa telefonom kroz istu Meta AI aplikaciju.

## Šta radi

- „**Hej Deda**" — otvara razgovor (Gemini uživo, kroz zvučnike naočara,
  vidi kroz kameru naočara)
- „**Ćao Deda**" — završava razgovor
- Pripravnost se pali duplim tapom na naočarima ili dugmetom u
  notifikaciji (bira se u Settings)
