# Split: product and design

The same product on three clients: web (`apps/web`), Android (`apps/android`, Kotlin + Compose) and
iOS (`apps/ios`, SwiftUI). This file fixes what must be the same everywhere: the screens, the words
and the colours. Each client uses its platform's own navigation, controls and typeface.

The interface is Dutch. Code, comments and identifiers are English.

## Screens

1. **Inloggen / Account maken.** E-mail and password; registering also asks a name. The mobile apps
   have a "Server" field behind "Geavanceerd" to point at another server.
2. **Groepen** (home). On top the balance over all groups, in words: "Je krijgt € 12,50",
   "Je bent € 8,00 verschuldigd" or "Je staat quitte". Then the groups: colour tile with initials,
   name, number of members, own balance. Actions: "Nieuwe groep" and "Deelnemen met code".
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

Amounts are formatted as Dutch euros: `€ 1.234,56`. A positive balance gets a plus sign and the
positive colour; a negative balance a minus sign in the normal text colour (owing money is not an
error, so no red); zero is grey. Red is only for destructive actions and errors.

## Colour

Light theme tokens (the mobile apps add a dark theme with the same roles):

| Role | Light | Dark |
| --- | --- | --- |
| Ground (page) | `#f3f5f7` | `#101214` |
| Surface (cards) | `#ffffff` | `#1a1d20` |
| Border | `#e6e6e6` | `#2c3034` |
| Text | `#1b1d1f` | `#eceef0` |
| Muted text | `#5c6064` | `#a0a5ab` |
| Accent (primary action) | `#1d5fd6` | `#7fb0ff` |
| On accent | `#ffffff` | `#06224f` |
| Positive | `#2f7a45` | `#6fcf8b` |
| Negative / destructive | `#c41c29` | `#ff8a8f` |

Cards have a 20 px radius, controls 10 px. No gradients, no shadows on things at rest.

### Tints

Groups and people have no pictures. A group is a rounded tile and a person a circle, filled with a
pale tint and showing initials in the dark ink of the same hue. A group stores its tint (`color`);
a member's tint is their position in `GroupDetail.members` modulo 8, in this order:

| Key | Background | Ink | Dark background | Dark ink |
| --- | --- | --- | --- | --- |
| `blue` | `#dcf1ff` | `#0a3a80` | `#12305c` | `#cfe6ff` |
| `amber` | `#fff1cf` | `#7a3e00` | `#4a3206` | `#ffe2a6` |
| `teal` | `#d5f3f1` | `#0b4f4a` | `#0e3d3a` | `#bdeeea` |
| `rose` | `#ffe1ec` | `#8a1846` | `#52142e` | `#ffc9dc` |
| `green` | `#dcfae5` | `#0b4a22` | `#113d20` | `#c2f2d0` |
| `coral` | `#ffe4dc` | `#8a2b12` | `#52200f` | `#ffcdbf` |
| `lime` | `#ecf6cc` | `#3f5200` | `#2f3b08` | `#e0f0a8` |
| `slate` | `#e9edf2` | `#2f3a46` | `#2a3138` | `#d7dee6` |

Initials: the first letter of the first two words ("Weekendje Texel" → "WT", "Anna" → "A").
