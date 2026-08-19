# Deda

Glasovni asistent za Ray-Ban Meta naocare (Gen 1/2) + Android telefon.
Radi i **bez naocara** — dugme „Start on Phone“ koristi kameru i mikrofon
telefona, pa prvo tako proveri da kljuc i jezik rade, a naocare dodaj posle.

## Instalacija

**Najnovija verzija (uvek ista adresa):**

### [PREUZMI deda.apk](https://github.com/UVuruna/deda/releases/latest/download/deda.apk)

> **Prvo ti treba besplatan Google kljuc.** Detaljan slikovni vodic (SR / SL / EN):
> **[Kako napraviti kljuc → vodic.html](vodic.html)** — otvori u pregledacu.

ili skeniraj telefonom:

![QR kod za preuzimanje](qr.png)

1. Otvori link (ili skeniraj QR) na telefonu i sacekaj da se `deda.apk` preuzme.
2. Otvori preuzeti fajl i potvrdi instalaciju:
   - Android pita za dozvolu instaliranja nepoznatih aplikacija — dozvoli za
     pregledac koji je skinuo fajl.
   - Ako **Play Protect** upozori („nepoznat programer / nebezbedna
     aplikacija“), izaberi **Install anyway / Ipak instaliraj**.
   - Na **Samsung** telefonu, ako instalacija cuti ili je odbijena: Podesavanja
     -> Bezbednost i privatnost -> **Auto Blocker** -> privremeno iskljuci, pa
     instaliraj i vrati ga.
3. Pri prvom pokretanju odobri trazene dozvole (mikrofon, obavestenja).
4. U Settings (zupcanik gore desno) nalepi **svoj** Gemini API kljuc — vidi
   vodic iznad. Kad izadjes nazad (strelicom ili prevlacenjem), unos se sacuva.
5. Proveri bez naocara: na pocetnom ekranu **Start on Phone** i postavi
   pitanje naglas — ako odgovori, kljuc i jezik rade.
6. Za naocare: u **Meta AI** aplikaciji ukljuci **Developer Mode** — na
   novijim verzijama: Settings -> App Info -> tapni **broj verzije 5 puta** ->
   pojavi se Developer Mode prekidac; na starijim je pod Privacy. Naocare
   moraju vec biti uparene sa telefonom kroz istu Meta AI aplikaciju.

## Sta radi

- „**Hej Deda**“ — otvara razgovor (Gemini uzivo, kroz zvucnike naocara,
  vidi kroz kameru naocara)
- „**Cao Deda**“ — zavrsava razgovor
- Pripravnost se pali duplim tapom na naocarima ili dugmetom u
  notifikaciji (bira se u Settings)
