# Split Sport: product and design

Split Sport is Split for sports clubs: every group is a **team** with a **team pot (teamkas)**, a
treasurer, contributions, a fine list and the usual shared expenses. The same product on three clients: web (`apps/web`), Android (`apps/android`, Kotlin + Compose) and
iOS (`apps/ios`, SwiftUI). This file fixes what must be the same everywhere: the screens, the words
and the house style. Each client keeps its platform's own navigation patterns and gestures.

Version 2 of the house style is called **Kassabon** and is described under "House style" below. The
interface designs it was built from are in [`docs/design/v2`](design/v2): `kassabon-mobiel-1.jpg`,
`kassabon-mobiel-2.jpg` and `kassabon-web.jpg` are the reference; when this text and a picture
disagree, this text wins.

The interface is Dutch. Code, comments and identifiers are English.

## The team pot in one paragraph

Every team has a pot. It is a member of the team that is not a person (`kind: "pot"`, named
"Teamkas", always last in `members`). Money questions then have two sides, and every client shows
them apart:

- **Met de teamkas**: contributions ("inleg") and fines ("boetes") are *charges*: the listed
  members owe the pot. An expense can be paid *from* the pot (the pot is the payer) or be *for* the
  pot (a member advanced it and the pot owes them). Paying the pot is a payment from a member to the
  pot; in real life that is a transfer to the **penningmeester**, whose IBAN is shown.
- **Onderling**: ordinary expenses between members, as in Split.

`balances[].potCents` and `balances[].peerCents` carry the two sides; `pot.cashCents` is the
**kasstand**: money that actually came in minus what went out. A fine that has not been paid is a
claim, not cash.

## Screens

1. **Inloggen / Account maken.** E-mail and password; registering also asks a name. The mobile apps
   have a "Server" field behind "Geavanceerd" to point at another server. Next to the wordmark a
   small stamp "SPORT".
2. **Teams** (home). On top a receipt "Jouw saldo" with the balance over all teams as a large figure
   ("+ € 109,55"), and in words: "Je krijgt € 12,50", "Je bent € 8,00 verschuldigd" or
   "Je staat quitte". Then one small receipt per team: name (underlined with the team's
   highlighter), a line "Hockey · MHC Oostpoort · 2026/27" (sport, club and season; club and season
   only when filled in), number of team members, own balance. Actions: "Nieuw team" and
   "Deelnemen met code".
3. **Team.** Three parts: "Uitgaven", "Teamkas" and "Saldo" (tabs on a phone; on a wide screen the
   list on the left and Teamkas, Saldo and Boetekoning receipts on the right).
   - *Uitgaven*: everything that happened, newest first, under month headings. An expense row shows
     the description, "Anna betaalde · 2 nov" (or "Teamkas betaalde"), the amount and what it means
     for you. A fine reads "Te laat ..... 2,00" with the fined member highlighted and a stamp
     "BOETE"; a contribution "Inleg november ..... 125,00" with "5 × 25,00" and a stamp "INLEG"; a
     payment "Bas betaalde Teamkas" with the stamp "BETAALD". Tapping an expense opens it for
     editing; fines, contributions and payments can only be removed.
   - *Teamkas*: the kasstand as the large figure, "Nog te ontvangen" and "Nog uit te betalen", the
     penningmeester (highlighted name, and the IBAN with a copy button when known), and then per
     member what they have with the pot: "Bas → Teamkas 12,00" or "Teamkas → Chris 40,00", each with
     "Markeer als betaald", which opens the payment sheet prefilled.
   - *Saldo*: every team member with their balance among each other (`peerCents`) and, under
     "Vereffenen", the suggested payments between members.
   - *Boetekoning*: the fine ranking, most first: highlighted name, "3×", total. The first one gets
     a small crown stamp "KONING". Hidden while nobody has a fine.
   - Actions: "Uitgave toevoegen" (primary), "Boete geven", "Inleg vragen". Team settings: name,
     highlighter, sport, club, season, penningmeester, the fine list (add, change, remove), team
     members (add by name, rename or remove those without an account), invite code with
     "Deel uitnodiging", leave team.
4. **Uitgave** (add / edit). Omschrijving, bedrag, datum, "Betaald door" (a team member or the
   Teamkas) and "Verdeling":
   - "Gelijk": tick who takes part; every row shows that person's amount. The Teamkas is in the
     list too, unticked by default; ticking only the Teamkas means "ten laste van de teamkas".
   - "Bedragen", "Aandelen", "Per regel": as in Split, with "Bon scannen" for the lines.
   - A helper "Rit berekenen": kilometres × a rate per km (default € 0,23) fills in the amount and,
     when it is empty, the description "Rit (84 km)".
   - Always a live "Per persoon" summary from the local `computeShares`.
5. **Boete geven.** Pick who (one or more team members, as chips), pick a line of the fine list
   (buttons "Te laat 2,00", "Gele kaart 5,00", …) or type an own description and amount, and a
   date. Saves one fine for the chosen members.
6. **Inleg vragen.** Omschrijving ("Inleg november"), bedrag per persoon, who (everyone ticked by
   default), datum. Shows the total ("5 × € 25,00 = € 125,00").
7. **Betaling** (settle up). Van, aan (team members or the Teamkas), bedrag, datum.
8. **Deelnemen.** Enter a code (or arrive by link). Shows the team with its sport and club and asks
   "Wie ben jij?" when the team has members without an account.
9. **Profiel.** Naam, e-mail (read-only), IBAN ("zodat teamgenoten weten waar ze kunnen betalen"),
   "Uitloggen", "Account verwijderen" with a confirmation.

Every list has an empty state that says what to do next, every request a loading state, and every
failure shows the server's `error.message`.

## Words

| Situation | Text |
| --- | --- |
| A group | "team"; its members "teamleden" ("1 teamlid", "5 teamleden") |
| Positive balance | "krijgt € 12,50" / "Je krijgt € 12,50" |
| Negative balance | "betaalt € 8,00" / "Je bent € 8,00 verschuldigd" |
| Zero | "quitte" / "Je staat quitte" |
| The pot | "Teamkas"; its cash "Kasstand"; its keeper "Penningmeester" |
| A charge to all | "Inleg" ("Inleg vragen"); a penalty "Boete" ("Boete geven"); the list "Boetelijst" |
| Empty teams | "Nog geen teams. Maak er een voor je elftal, zevental of trainingsgroep." |
| Empty expenses | "Nog niets genoteerd. Vraag de eerste inleg of voeg een uitgave toe." |
| Everyone square | "Iedereen staat quitte." |
| Nothing with the pot | "Niemand heeft nog iets openstaan bij de teamkas." |
| Member without account | "geen account" |
| You | "jij" (after the name: "Anna (jij)") |

The sports and their labels are in `SPORT_LABELS` (`packages/core/src/api-types.ts`): Voetbal,
Hockey, Korfbal, Volleybal, Handbal, Basketbal, Tennis, Padel, Rugby, Waterpolo, Overig. A new
team's season defaults to the running one, July to June ("2026/27").

Amounts are formatted the Dutch way: `1.234,56`. On receipt lines the euro sign is left out, as on a
real receipt ("Wedstrijdballen ....... 47,50"); totals, headline figures and sentences carry it
("KASSTAND € 47,50", "Je krijgt € 24,95"). A balance always has its sign: "+ 24,95", "− 21,60"; zero is
"0,00" in the muted ink. There is no green or red for balances: the sign says it. Red is only for
stamps, destructive actions and errors.

## House style: Kassabon (v2)

The idea comes from the thing the app is about: **the paper till receipt, and the highlighter you
use to mark what was yours.** Every panel is a piece of receipt paper, every number is printed like
a till prints it, and every person is a highlighter colour.

### Colour

| Role | Light | Dark |
| --- | --- | --- |
| Desk (page ground) | `#e8e5de` | `#171614` |
| Paper (receipts, sheets, dialogs) | `#fbf8f1` | `#24221f` |
| Ink (text, primary buttons, outlines) | `#141414` | `#f4f0e7` |
| On ink (text on a primary button) | `#fbf8f1` | `#171614` |
| Muted ink (labels, secondary lines) | `#615d55` | `#a9a499` |
| Perforation (dashed dividers, field underlines) | `#b5b0a5` | `#4a463f` |
| Stamp red ("BETAALD", destructive, errors) | `#d0342c` | `#ff6b5e` |

Text that lies on a highlighter is always `#141414`, in both themes. No gradients and no shadows
anywhere; the only depth is paper on the desk.

### Highlighters

A group stores one of eight keys (`color`); a member's highlighter is their position in
`GroupDetail.members` modulo 8, in this order:

| # | Key | Highlighter | Colour |
| --- | --- | --- | --- |
| 1 | `amber` | geel | `#fff04a` |
| 2 | `rose` | roze | `#ff8ccf` |
| 3 | `green` | mint | `#8af0ae` |
| 4 | `coral` | oranje | `#ffae5c` |
| 5 | `blue` | blauw | `#8fd4ff` |
| 6 | `lime` | limoen | `#d2f25a` |
| 7 | `teal` | turquoise | `#74e6da` |
| 8 | `slate` | grijs | `#d8d4c9` |

The colours are the same in the dark theme.

### Type

Two typefaces, both open source (SIL OFL) and bundled with every client:

- **Barlow Condensed Bold (700)** for everything that is "printed big": the wordmark, screen titles
  (30 on a phone, 40 on desktop), receipt titles (22), buttons and tabs (17–18).
- **JetBrains Mono** (400, 500, 700) for everything a till would print: all amounts, receipt lines,
  labels, field values and running text (15; small 13; headline figure 30 at weight 500).

Month headings on a receipt are uppercase mono ("NOVEMBER 2026"), not letter-spaced.

### Elements

- **Wordmark.** "Split" in Barlow Condensed Bold with the stamp "SPORT" after it, torn in two: a vertical dashed perforation line in
  ink runs between "Sp" and "lit", extending a little above and below the letters, with a small gap
  between the halves.
- **Receipt.** Paper fill, no border, no shadow, top corners 2 px. The bottom edge is a zigzag tear:
  teeth 12 wide and 6 high. Padding 16–20. Sections on one receipt are separated by a
  **perforation**: a 1.5 px dashed line (dash 6, gap 4) in the perforation colour.
- **Leader.** A receipt line is "name ........ amount": a dotted leader (2 px dots, 6 px apart, in
  the perforation colour) fills the space between the two, sitting on the text baseline.
- **Highlighter.** A person is never an avatar: a person is their name with their highlighter
  swiped behind it. The stroke is a little taller than the lowercase letters, sticks out about 6 px
  left and right, has slightly uneven ends and is tilted about −1°. A group's name in a list is
  underlined with a thick stroke (the lower 40 % of the text) in the group's colour; in the dark
  theme, where light letters would be unreadable on it, the stroke sits just below the letters. On a receipt
  split "per regel", each line is swiped with the colours of the people who had it; shared by
  several people, the stroke is divided into equal parts left to right, in member order.
- **Person chips** (to say who had a receipt line): the name in Barlow Condensed on a filled pill in
  that person's colour when selected; unselected, a pill with a dashed perforation outline and
  muted text.
- **Stamp.** A rubber stamp: Barlow Condensed Bold, uppercase, 2 px outline in the text colour,
  corner radius 3, rotated −8°. A payment carries "BETAALD" and a fine "BOETE" in stamp red; a
  contribution carries "INLEG" and the wordmark "SPORT" in ink; the fine ranking's leader "KONING"
  in stamp red.
- **Teamkas.** The pot is not a person, so it has no highlighter: its name is printed as a label,
  on-ink text on an ink fill with the same slightly tilted stroke shape as a highlighter.
- **Buttons.** Primary: ink fill, on-ink label, radius 4, 48 high on a phone and 44 on desktop.
  Secondary: 1.5 px ink outline on paper. Destructive: stamp red, underlined text or red outline.
- **Tabs and segmented controls.** 1.5 px ink outline, radius 4; the selected segment is filled
  with ink.
- **Fields.** No boxes: a small muted mono label, the value in mono below it, and a dashed
  perforation underline that turns solid ink on focus.
- **Checkbox.** A 20 px square with a 1.5 px ink outline, radius 2; checked, it is filled with ink.

The app icon is `docs/design/v2/icon.svg` (PNG at 1024 px next to it): a receipt with its zigzag
tear, a perforation down the middle and one highlighter stroke, on the ink ground. Split Sport uses
the mint highlighter (`#8af0ae`) for the stroke, so the two apps can be told apart on a phone.
