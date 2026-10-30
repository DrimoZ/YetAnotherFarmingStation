# 00 — Analyse : farming compact, serres et ce qui manque

> Document de cadrage, avant toute décision. Les décisions prises sont dans
> [`01-DESIGN.md`](01-DESIGN.md). Les décisions tranchées partiront dans
> `CONTEXT.md` (vocabulaire) et `docs/adr/` (choix difficiles à défaire).
> Les détails marqués *(à vérifier)* viennent de mémoire et n'ont pas été recontrôlés.

---

## 1. Ce que font les autres jeux

| Jeu | Mécanique | Ce qu'on en retient |
|---|---|---|
| **Factorio — Space Age (Gleba)** | Tour agricole qui plante/récolte dans un rayon ; fruits **périssables** (spoilage) ; biochambre nourrie en nutriments | Une machine qui *gère* une zone plutôt qu'une boîte noire ; le temps comme ressource |
| **Oxygen Not Included** | Bacs hydroponiques ; chaque plante exige température, atmosphère, pression, irrigation, engrais ; plantes **sauvages vs domestiquées** (×4) ; mutations | Les **exigences d'environnement** lisibles dans un panneau : c'est le jeu de référence |
| **RimWorld** | Bassins hydroponiques (fertilité 280 %), lampe solaire, température de la pièce | La pièce fermée *est* la serre — l'enceinte compte |
| **Vintage Story** | Terre à nutriments **N/P/K**, rotation des cultures, serre = pièce fermée vitrée (+°C), cloche à froid | Rotation et nutriments en survie « Minecraft-like » |
| **Captain of Industry** | Fermes avec **fertilité %**, rotation, pluie, serre = amélioration (toute l'année, consomme de l'eau) | La serre comme *palier* d'une même ferme, pas un autre bloc |
| **Stardew Valley** | Serre hors saison, arroseurs à rayon, engrais typés (qualité / vitesse / rétention), **qualité** ★, plantes géantes | Variété d'engrais = choix, pas juste « plus vite » |
| **Anno 1800** | Fertilité par île : une culture n'est possible que là où elle est autorisée | Gating par biome / climat |
| **Farming Simulator** | États de champ : chaulage, engrais en plusieurs passes, mauvaises herbes | Trop de micro-gestion : contre-exemple |
| **Subnautica / NMS / Planet Crafter** | Bacs intérieurs, minuteur fixe | Le minimum viable : poser, attendre, récolter |
| **Satisfactory / DSP** | Aucune agriculture | Le créneau est vide même hors Minecraft côté « usine » |

**Tendance commune** : les jeux mémorables font de l'*environnement* le gameplay (ONI,
RimWorld, Vintage Story). Les jeux d'usine font de la *cadence* le gameplay (Factorio).
Presque personne ne fait les deux bien.

---

## 2. Ce qui existe en mod Minecraft

### 2.1 Boîtes noires compactes (graine → items)

| Mod | Forme | Remarque |
|---|---|---|
| **Botany Pots** (Darkhax) | Pot 1 bloc, variantes hopper, rendu de la plante | La référence ; >1 300 cultures via addons/datapacks ; multi-loader |
| **BotanyPotsAdditions** (NeoForge 1.21.1) | Pot « serre » vitré, pots-arroseurs (énergie + eau), pots multi-cellules | Preuve que la demande « serre » existe, mais en addon |
| **Immersive Engineering — Garden Cloche** | Bloc 2 de haut, sol + graine, FE, engrais fluide | Recettes data-driven ; rendu de la plante sous verre |
| **Thermal — Phytogenic Insolator** | Machine 1 bloc, eau + FE | Aucun visuel de la plante |
| **Mystical Agriculture** | Cultures d'essence + accélérateurs | Le farming comme source de ressources |

### 2.2 Machines de zone (vraies plantes dans le monde)

| Mod | Forme | Remarque |
|---|---|---|
| **Ender IO — Farming Station** (1.7–1.12) | 1 bloc, zone autour, outils en slots | Le nom qu'on cite encore 10 ans après |
| **MineFactory Reloaded / Industrial Foregoing** | Planteur + récolteur + fertiliseur séparés, addons de portée, zone affichée | Modularité par blocs séparés |
| **Oritech** | Machines de ferme alimentant le biocarburant ; **addons physiques collés à la machine** *(détail à vérifier)* | Les améliorations se *construisent* au lieu de se glisser en slot |
| **Create** | Récolteuses sur contraptions | Gameplay mécanique, pas de bloc |

### 2.3 Serres « vraies » (structures)

| Mod | Forme | Remarque |
|---|---|---|
| **Forestry — Multifarm / Greenhouses** | Multibloc bâti, climat (température, humidité) | Riche mais lourd, UI datée |
| **Firmalife** (addon TerraFirmaCraft) | Serre en blocs dédiés, **station climatique** qui valide l'enceinte, planters variés (hydroponique, treillis, suspendu), arroseurs | Le précédent le plus proche de l'idée « serre vivante » ; enfermé dans TFC |
| **Serene Seasons** | Verre de serre : les cultures poussent hors saison | Compat évidente |

### 2.4 Cousins utiles pour « d'autres choses plus tard »

- **Hostile Neural Networks** : farm de mobs *simulée* dans un bloc à partir d'un modèle de
  données. Preuve qu'on peut simuler sans entité.
- **Productive Bees / Productive Trees** : arbres et abeilles comme cultures.
- **Agricraft** : génétique (croissance / rendement / résistance), croisements, analyseur.
- **Compact Machines** : un vrai volume dans un bloc — l'autre sens de « compact ».

---

## 3. Ce qui est sous-représenté

1. **L'environnement dans un mod tech.** Les machines (Cloche, Insolator, Pots) ignorent la
   lumière, l'eau, le biome. Les mods qui les prennent en compte (Forestry, Firmalife) ont
   une UX de 2014 ou sont liés à un overhaul. Créneau : *exigences lisibles + UI moderne*.
2. **Une serre qui se voit.** Un volume vitré où l'on voit pousser 20 cultures différentes,
   rendu efficacement (pas de vraie entité ni de vrai bloc par plante) : très rare.
3. **Le diagnostic.** Aucune machine de ferme ne dit *pourquoi* elle ne produit pas
   (« lumière 7/9 », « eau manquante », « sortie pleine »). Ta charte (`Status`
   WORKING / WAITING / BLOCKED / PROBLEM / OFF, voyant + onglet d'info) y répond déjà.
4. **Compat zéro-config.** Tout le monde exige une définition JSON par culture. Déduire
   une culture d'un `CropBlock` + sa loot table, avec JSON en surcharge seulement, serait
   un vrai différenciateur. Faisable : simuler la table de loot de l'état mûr ; à
   encadrer pour les cas exotiques (canne, cactus, plantes à bloc-entité, cultures 2 blocs).
5. **Plantes compagnes / synergies.** Bonus quand X pousse à côté de Y : quasiment absent
   de Minecraft, naturel dans une serre à parcelles.
6. **Cadence affichée.** Items/min réels, goulot nommé — standard Factorio, absent ici.

À éviter : la micro-gestion à la Farming Simulator, le périssable punitif hors d'un mod
dont c'est le thème, et l'« imprimante à items » qui rend toute ferme vanilla inutile.

---

## 4. Ce que tu as déjà (FactoryIO, Portable Beacons)

Réutilisable tel quel, ou comme modèle :

| Acquis | Où | Usage ici |
|---|---|---|
| Grille GUI 176 px alignée sur l'inventaire, jauge colonne 0, voyant d'état | `FactoryIO/shared/GuiMetrics` | même charte, même famille visuelle |
| Palette par fonction, 5 statuts, onglets jaune = info / bleu = augments | `FactoryIO/client/gui/GuiTheme` | diagnostic des parcelles |
| Tableau « une ligne par élément, tout réglable sur place », sélecteur modal en grille | Portable Beacons, `DESIGN.md` §5 | une ligne par parcelle / culture |
| Multibloc posé d'un coup, maître + parties invisibles, capabilities déléguées, contour fantôme rouge/vert | `FactoryIO/content/multiblock`, `docs/12` §2 | si la serre est « posée » |
| Augments = un seul item + composant data-driven | Portable Beacons, §7 | modules de serre |
| Tout en registries datapack avec network codec | les deux | cultures, sols, engrais, modules |
| Séparation `core/` pur testable / `platform/` | Portable Beacons, §9 | backport/port facile |

**Écart de pile** : FactoryIO est Forge 1.20.1, Portable Beacons NeoForge 26.1 (Java 25,
pas d'obfuscation). La charte GUI est à porter, pas à copier.

---

## 5. Comment je le verrais (proposition, à griller)

**Idée centrale** : une serre dont on *voit* l'intérieur, qui *explique* ce qu'elle fait,
et où l'environnement fait le choix — pas le nombre d'upgrades.

- **Parcelle** : l'unité de culture (sol + plante + état de croissance), simulée, rendue.
- **Serre** : l'enceinte qui fournit l'environnement (lumière, eau, température) aux
  parcelles. Le climat est *une valeur de la serre*, pas un calcul par bloc.
- **Exigences** par culture, déduites (biome d'origine, lumière vanilla) et surchargeables.
  Écart = croissance ralentie, jamais un blocage silencieux : le panneau dit pourquoi.
- **Modules** physiques (lampes, arroseurs, chauffage) : items data-driven, à la Portable
  Beacons.
- **Sortie** par capability standard — tes inserters FactoryIO, des hoppers, n'importe quoi.
- **Plus tard** : arbres, champignons, aquatique ; animaux/mobs simulés (modèle HNN). Rien
  de cela dans le premier jet.

Risques principaux : rendu de nombreuses plantes (perf client), compat des cultures
exotiques, équilibre face aux fermes vanilla.

---

## Sources consultées

- [Botany Pots — CurseForge](https://www.curseforge.com/minecraft/mc-mods/botany-pots)
- [BotanyPotsAdditions — CurseForge](https://www.curseforge.com/minecraft/mc-mods/botanypotsadditions)
- [Oritech — moddedmc.wiki](https://moddedmc.wiki/project/oritech)
- `FactoryIO/docs/12-DESIGN-CRAFTER.md`, `FactoryIO/shared/GuiMetrics.java`,
  `FactoryIO/client/gui/GuiTheme.java`, `BeaconPack/DESIGN.md`, `BeaconPack/CLAUDE.md`
