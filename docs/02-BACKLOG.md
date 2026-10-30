# 02 — Backlog

Backlog priorisé et estimé. Chaque ticket est autonome et vérifiable. La spécification est
[`01-DESIGN.md`](01-DESIGN.md) ; un ticket qui la contredit se trompe, ou la fait évoluer.

**Priorité** : `P0` socle, rien ne marche sans · `P1` première version · `P2` important · `P3` souhaitable
**Estimation** : `XS` <1 h · `S` 1-4 h · `M` 0,5-2 j · `L` 3-5 j · `XL` >1 sem

Colonne « ✓ » = critère d'acceptation. `~~YAF-xxx~~ ✅` = fait. `❄️` = gelé : ni abandonné, ni planifié.

## 🧭 Ordre de marche

1. **Une serre P1 qu'on pose et qu'on casse** — A.
2. **Une parcelle de blé qui pousse et se récolte** — B (sauf détection complète), C minimal.
3. **Le risque n°1 tout de suite** : voir 4 plantes pousser dans la serre — E.
4. **L'écran** — D.
5. Ensuite, dans l'ordre : détection complète, ports, modules, paliers supérieurs, intégrations.

Règle : un ticket n'est fait qu'avec son test (JUnit pour `core/`, GameTest pour ce qui demande
un monde).

---

## Épic A — La serre posée

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-001~~ | ✅ | M | **Porter le cadre multibloc** — **fait** de FactoryIO (`MultiblockShape`, `MultiblockBlock`, `MultiblockPartBlock`, `MultiblockPartBlockEntity`) vers NeoForge 1.21.1. Capabilities : enregistrement par `RegisterCapabilitiesEvent`, la partie renvoie celle du maître. | `MultiblockShapeTest` porté passe |
| ~~YAF-002~~ | ✅ | S | **Serre P1 posable** — **fait** : item, bloc maître, volume 3×3×3, modèle provisoire. | GameTests : pose bloquée si un bloc gêne ; casse depuis chaque partie, l'item tombe une seule fois |
| 🟡 YAF-003 | P0 | S | **Contour fantôme de pose** (client) — **fait, reste à le voir en jeu** : vert si `canPlace`, rouge sinon. | vu en `runClient` |
| ~~YAF-004~~ | ✅ | XS | **Fait** — onglet créatif, clés `en_us` / `fr_fr`. | `TranslationsTest` : les deux fichiers ont les mêmes clés |
| ~~YAF-005~~ | ✅ | S | **Paliers P2 et P3** — **fait** (`GreenhouseTier` ; modèle provisoire réduit à un socle, contour du volume dessiné, en attendant YAF-090) : définition palier → volume, parcelles, emplacements de module, ports, densité max. | GameTests : pose de P2 et P3 |
| 🟡 YAF-006 | P1 | M | **Monter de palier sur place** — **fait, contour à voir en jeu** : l'item du palier suivant utilisé sur une serre posée agrandit le volume, contour fantôme, refus si la place manque. | GameTests : contenu conservé ; place manquante → rien ne change, item non consommé |

## Épic B — Parcelles et cultures

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-010~~ | ✅ | S | **`core/` : la parcelle** — **fait** : `PlotGrowth` (progression, densité) et son `Codec` borné. Culture, substrat et catalyseur sont un inventaire de la serre, pas une donnée de `core/`, qui ne connaît pas les items. | JUnit : aller-retour du codec |
| ~~YAF-011~~ | ✅ | S | **`core/` : efficacité et loi du minimum** — **fait** (`Efficiency`) — plages, baisse linéaire, plancher 0.1, eau vide = 0, facteur limitant nommé. | JUnit : chaque facteur limitant, plancher, eau vide |
| ~~YAF-012~~ | ✅ | M | **Croissance simulée** — **fait** dans le maître, par pas de 20 ticks ; récolte vers la réserve de sortie ; graines en trop ; parcelle mûre en BLOCKED si la sortie est pleine. Blé codé en dur en attendant YAF-013. | GameTest : un blé pousse et se récolte ; sortie pleine → rien de perdu |
| ~~YAF-013~~ | ✅ | M | **Détection automatique** — **fait** (`Crops`, 8 cultures vanilla en 11 ms ; calculée des deux côtés plutôt que transmise, elle ne dépend que des registres et des tags) : `BlockItem` → bloc de culture, `canSurvive` sur un mini-monde fictif par substrat candidat, récolte par la table de loot de l'état mûr. Cache par rechargement, network codec. | GameTest : blé, carotte, pomme de terre, betterave, verrue du Nether reconnus avec les bons substrats |
| ~~YAF-014~~ | ✅ | M | **Registres datapack** `yafs/crop` et `yafs/substrate` — **fait** : fichiers JSON relus à chaque `/reload` (et non registres dynamiques, qui ne se rechargent qu'au lancement du monde), codecs bornés, synchronisés aux joueurs ; une surcharge remplace la détection champ par champ. Les exigences vanilla et la règle « terre = terre labourée » sont passées en JSON. | JSON invalide refusé et journalisé ; surcharge appliquée après `/reload` |
| ~~YAF-015~~ | ✅ | M | **Cas exotiques** — **fait** par des JSON livrés et un champ `harvest` (le bloc dont le loot donne la récolte) : canne à sucre, cactus, melon et citrouille (tige → fruit), urne (2 blocs), cacao. Aucune culture vanilla n'a de bloc-entité : ce cas n'est pas testé. | un GameTest par cas |
| ~~YAF-016~~ | ✅ | S | **Exigences vanilla livrées** — **fait**, en dur dans `Crops` jusqu'à YAF-014 ; test fait sur la verrue, la canne attendant YAF-015 (températures par culture) ; défaut tolérant pour le reste. | GameTest : la canne pousse moins vite en taïga qu'en désert |
| YAF-017 | P2 | S | **Substrats à bonus** (« terre fertile » : +rendement). | GameTest : rendement accru |

## Épic C — Environnement et ressources

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-020~~ | ✅ | S | **Environnement de base** — **fait** : lumière du ciel au maître, biome → °C, pluie ; recalcul toutes les 100 ticks. | JUnit : conversion biome → °C ; GameTest : sans ciel, lumière 0 |
| ~~YAF-021~~ | ✅ | S | **Réserve d'eau** — **fait** (8 000 mB, 500 mB par croissance, seau ou tuyau sur n'importe quel bloc en attendant les ports) : réservoir de fluide, consommation par cycle × densité, eau vide → PROBLEM. | GameTest : sans eau, rien ne pousse et le statut le dit |
| YAF-022 | P2 | S | **Engrais** : registre `yafs/fertilizer`, vitesse **ou** rendement. | GameTest : la poudre d'os accélère |
| YAF-023 | P2 | S | **Résidus** : compost avec une probabilité par culture ; réserve pleine → BLOCKED. | GameTest : conservation, blocage |

## Épic D — Interface

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-030~~ | ✅ | M | **Porter la charte GUI** — **fait** de FactoryIO (`GuiMetrics`, `GuiTheme`, `GuiSprites` et sa planche). | un écran vide de 176 px s'ouvre en `runClient` |
| 🟡 YAF-031 | P0 | M | **Écran de la serre** — **fait, reste à le voir en jeu** : grille de parcelles (barre de croissance, liseré de statut), jauge d'eau colonne 0, voyant. Valeurs par `ContainerData`, boutons par `clickMenuButton`. | `runClient` et `runServer` : la croissance avance à l'écran |
| 🟡 YAF-032 | P1 | M | **Détail d'une parcelle** en fenêtre superposée — **fait, à voir en jeu** (trois vrais slots du menu, branchés sur la parcelle choisie par bouton de menu) : emplacements Culture / Substrat / Catalyseur, exigences comparées à l'environnement. | poser et retirer une culture depuis l'écran ; substrat incompatible refusé par le serveur |
| 🟡 YAF-033 | P1 | S | **Onglet Environnement** (jaune) — **fait, à voir en jeu** : 3 facteurs, « Limité par » (la parcelle la plus lente), récoltes/min plutôt qu'items/min (le nombre d'items d'une récolte est aléatoire), eau/min. | la ligne « Limité par » change quand on pose une lampe |
| ~~YAF-034~~ | ✅ | S | **Statuts** — **fait** : les 5 états par parcelle, la serre prend le plus grave (`PlotStatus.worst`) ; un signal redstone sur le volume éteint tout (OFF). | JUnit sur la résolution du statut |

## Épic E — Rendu

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| 🟡 YAF-040 | P0 | M | **Prototype de rendu des plantes** — **fait, reste à le voir en jeu** : le maître dessine chaque parcelle avec le modèle baked de son stade ; le client n'est prévenu que quand un stade change. | 4 blés visibles qui grandissent en `runClient` |
| YAF-041 | P1 | S | **Budget de performance** : mesure de 50 serres P3 pleines, niveau de détail selon la distance. Budget versionné dans `docs/`. | chiffres consignés, budget tenu |

## Épic F — Ports

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-050~~ | ✅ | M | **Item Port** — **fait** (le port est dans l'état du bloc, visible sans synchronisation ; ports rendus à la casse et à la montée de palier) : transforme une partie de mur en port, plafond par palier ; seuls les ports exposent des capabilities. | GameTest : un hopper sous une partie normale ne tire rien, sous un port il tire |
| ~~YAF-051~~ | ✅ | M | **Modes de port** — **fait** (le filtre de sortie est un mode : sortie tout / récolte / graines ; une réserve d'énergie de 10 000 FE attend les modules ; pas de sortie de fluide tant qu'il n'y a pas de résidu fluide) : entrée/sortie d'items, entrée de fluide, entrée d'énergie ; filtre de sortie (récolte / graines / résidus / tout). | GameTests par mode |
| YAF-052 | P2 | XS | Changer le mode d'un port à la clé. Pour l'instant, on le change en réutilisant l'item Port : la clé ne vaut la peine que si un configurateur sert ailleurs. | — |

## Épic G — Modules

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| 🟡 YAF-060 | P1 | M | **Item module** à composant + registre `yafs/module` ; emplacements dans l'onglet bleu, effet recalculé en direct. — **fait** (registre de datapack, rechargé au lancement du monde ; effets dans l'infobulle de l'item, l'effet sur la serre s'affichera avec l'onglet Environnement, YAF-033 ; onglet à voir en jeu) | un module ajouté en datapack apparaît et fonctionne |
| ~~YAF-061~~ | ✅ | S | Lampe de croissance, Chauffage, Refroidisseur (FE) — **fait** : 20, 40 et 40 FE/t, sans énergie ils ne font rien. | GameTest : la verrue pousse dans l'Overworld avec un chauffage |
| ~~YAF-062~~ | ✅ | S | Collecteur de pluie — **fait** : 25 mB par pas sous la pluie ; « ciel visible » vient de `isRainingAt`, le test impose la pluie. | GameTest : la réserve monte sous la pluie, ciel visible requis |
| YAF-063 | P2 | S | Accélérateur, cumulable, coût croissant. | JUnit : coût proportionnel |
| YAF-064 | P2 | S | Densité et Capacité. | GameTest : densité ×2 double la récolte **et** la consommation |
| YAF-065 | P2 | S | Composteur (résidus → engrais). | GameTest : la boucle tourne seule |

## Épic H — Progression

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| ~~YAF-070~~ | ✅ | S | Recettes : paliers, port, modules — **fait**, en JSON écrits à la main plutôt qu'en datagen (huit recettes ne valent pas l'outillage). P3 demande du melon scintillant. | `RecipeGameTests` : tout se fabrique en survie |
| YAF-071 | P2 | XS | Config serveur : vitesse globale, résidus on/off, plafond de densité. | — |

## Épic I — Intégrations

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| YAF-080 | P1 | M | JEI : catégorie cultures (substrats, catalyseur, exigences, récolte). | lancé sans JEI : rien ne casse |
| YAF-081 | P1 | S | Jade : statut et facteur limitant au survol. | idem sans Jade |
| YAF-082 | P1 | S | Mystical Agriculture : surcharges des cruxes. | une graine à crux pousse avec son catalyseur, pas sans |
| ❄️ YAF-083 | P3 | S | Serene Seasons : la saison décale la température. | deuxième version |
| ❄️ YAF-084 | P3 | M | Agricraft : statistiques des graines. | deuxième version |

## Épic J — Publication

| ID | P | Est. | Ticket | ✓ |
|---|---|---|---|---|
| YAF-090 | P2 | M | Modèles et textures définitifs de la serre, des modules et du port. | — |
| YAF-091 | P2 | S | README, page de store, wiki joueur. | chaque chiffre cité existe dans les données |
