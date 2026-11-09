# Split: product and design

The same product on three clients: web (`apps/web`), Android (`apps/android`, Kotlin + Compose) and
iOS (`apps/ios`, SwiftUI). This file fixes what must be the same everywhere: the screens, the words
and the house style. Each client keeps its platform's own navigation patterns and gestures.

Version 2 of the house style is called **Kassabon** and is described under "House style" below. The
interface designs it was built from are in [`docs/design/v2`](design/v2): `kassabon-mobiel-1.jpg`,
`kassabon-mobiel-2.jpg` and `kassabon-web.jpg` are the reference; when this text and a picture
disagree, this text wins.

The interface is Dutch. Code, comments and identifiers are English.

## Screens

1. **Inloggen / Account maken.** E-mail and password; registering also asks a name. The mobile apps
   have a "Server" field behind "Geavanceerd" to point at another server.
2. **Groepen** (home). On top a receipt "Jouw saldo" with the balance over all groups as a large
   figure ("+ € 109,55"), and in words: "Je krijgt € 12,50", "Je bent € 8,00 verschuldigd" or
   "Je staat quitte". Then one small receipt per group: name (underlined with the group's
   highlighter), number of members, own balance. Actions: "Nieuwe groep" and "Deelnemen met code".
3. **Groep.** Two parts: "Uitgaven" and "Saldo".
   - *Uitgaven*: newest first. A row shows the description, "Anna betaalde · 2 nov", the amount and
     what it means for you ("je krijgt € 6,66" / "je betaalt € 3,33"; nothing when you are not in it).
     A payment between members reads "Bas betaalde Anna". Tapping a row opens it for editing.
   - *Saldo*: every member with their balance, and under "Vereffenen" the suggested payments
     ("Bas betaalt Anna € 3,33") each with "Markeer als betaald", which opens the payment sheet
     prefilled. When the receiver has an IBAN it is shown and can be copied.
   - Primary action: "Uitgave toevoegen". Group settings: rename, colour, members (add by name,
     rename or remove members without an account), invite code with "Deel uitnodiging", leave group.
4. **Uitgave** (add / edit). Omschrijving, bedrag, datum, "Betaald door", and "Verdeling":
   - "Gelijk": tick who takes part; every row shows that person's amount.
   - "Bedragen": an amount per person; shows how much is still to divide ("Nog € 2,50 te verdelen").
   - "Aandelen": a stepper per person (0 = not in).
   - "Per regel": the lines of the receipt, each with name, amount and the members as toggles.
     "Bon scannen" (camera or photo library) fills the lines, the total, the date and the description
     from a photo; only shown when the server supports it. When total and lines differ:
     "Verschil van € 4,00 wordt naar rato verdeeld."
   - Always a live "Per persoon" summary from the local `computeShares`.
   - Editing adds "Verwijderen" with a confirmation.
5. **Betaling** (settle up). Van, aan, bedrag, datum. Saved as a settlement.
6. **Deelnemen.** Enter a code (or arrive by link). Shows the group and asks "Wie ben jij?" when the
   group has members without an account: pick your name, or "Ik sta er niet tussen".
7. **Profiel.** Naam, e-mail (read-only), IBAN ("zodat anderen weten waar ze kunnen betalen"),
   "Uitloggen", "Account verwijderen" with a confirmation.

Every list has an empty state that says what to do next, every request a loading state, and every
failure shows the server's `error.message`.

## Words

| Situation | Text |
| --- | --- |
| Positive balance | "krijgt € 12,50" / "Je krijgt € 12,50" |
| Negative balance | "betaalt € 8,00" / "Je bent € 8,00 verschuldigd" |
| Zero | "quitte" / "Je staat quitte" |
| Empty groups | "Nog geen groepen. Maak er een voor je huis, vakantie of etentje." |
| Empty expenses | "Nog geen uitgaven. Voeg de eerste toe." |
| Everyone square | "Iedereen staat quitte." |
| Member without account | "geen account" |
| You | "jij" (after the name: "Anna (jij)") |

Amounts are formatted the Dutch way: `1.234,56`. On receipt lines the euro sign is left out, as on a
real receipt ("Boodschappen Jumbo ....... 64,12"); totals, headline figures and sentences carry it
("TOTAAL € 84,00", "Je krijgt € 24,95"). A balance always has its sign: "+ 24,95", "− 21,60"; zero is
"0,00" in the muted ink. There is no green or red for balances: the sign says it. Red is only for
the "BETAALD" stamp, destructive actions and errors.

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

- **Wordmark.** "Split" in Barlow Condensed Bold, torn in two: a vertical dashed perforation line in
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
  underlined with a thick stroke (the lower 40 % of the text) in the group's colour. On a receipt
  split "per regel", each line is swiped with the colours of the people who had it; shared by
  several people, the stroke is divided into equal parts left to right, in member order.
- **Person chips** (to say who had a receipt line): the name in Barlow Condensed on a filled pill in
  that person's colour when selected; unselected, a pill with a dashed perforation outline and
  muted text.
- **Stamp.** A payment between members carries a rubber stamp "BETAALD": Barlow Condensed Bold,
  uppercase, stamp red, 2 px outline in the same red, corner radius 3, rotated −8°.
- **Buttons.** Primary: ink fill, on-ink label, radius 4, 48 high on a phone and 44 on desktop.
  Secondary: 1.5 px ink outline on paper. Destructive: stamp red, underlined text or red outline.
- **Tabs and segmented controls.** 1.5 px ink outline, radius 4; the selected segment is filled
  with ink.
- **Fields.** No boxes: a small muted mono label, the value in mono below it, and a dashed
  perforation underline that turns solid ink on focus.
- **Checkbox.** A 20 px square with a 1.5 px ink outline, radius 2; checked, it is filled with ink.

The app icon is `docs/design/v2/icon.svg` (PNG at 1024 px next to it): a receipt with its zigzag
tear, a perforation down the middle and one yellow highlighter stroke, on the ink ground.
