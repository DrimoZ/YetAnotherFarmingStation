# 01 — Design : la serre

> Mod NeoForge 1.21.1, Java 21. Port prévu vers 26.x (voir §13).
> Vocabulaire : [`CONTEXT.md`](../CONTEXT.md). Origine des choix : [`00-ANALYSE.md`](00-ANALYSE.md).
> Chaque section dit aussi ce qui a été écarté, pour ne pas rouvrir le débat par accident.
> Les chiffres sont des valeurs de départ, tous en datapack ou en config : à régler en jeu.

---

## 1. Concept

Une **serre** multibloc où l'on fait pousser des cultures en parallèle. Ce n'est pas une boîte
noire : l'**environnement** du lieu (lumière, eau, température), corrigé par des **modules**,
décide de la vitesse, et l'écran dit toujours *pourquoi* ça pousse lentement.

Trois promesses, dans l'ordre :

1. **On voit** les plantes pousser dans la serre.
2. **On comprend** : statut, facteur limitant, cadence en items/min.
3. **Ça marche avec tout** : les cultures des autres mods sont reconnues sans configuration.

Le nom est ironique, et c'est voulu : « encore une ferme de plus », alors que le mod ne ressemble
à aucune autre. Dans le jeu comme dans le code, on dit **serre**, jamais « station ».

---

## 2. La serre

### 2.1 Pose

Un item pose tout le volume d'un coup (voir [ADR 0001](adr/0001-serre-posee-pas-construite.md)).
On reprend le cadre multibloc de FactoryIO (`docs/12` §2) :

- Le bloc visé devient le **centre-bas** du volume ; la façade regarde le joueur.
- Tenir l'item dessine un **contour fantôme**, vert si la pose est possible, rouge sinon. Le
  verdict vient de la même méthode `canPlace` qui décide réellement de la pose.
- Le **maître** porte toute la logique. Les **parties** sont invisibles et ne connaissent que le
  décalage relatif vers le maître.
- Casser n'importe quel bloc retire tout le volume. Seul le maître lâche l'item et le
  **contenu entier** : graines, substrats, catalyseurs, modules, réserves.
- Avant toute conclusion, on vérifie `level.isLoaded(masterPos)` : le volume peut chevaucher
  une frontière de chunk.

Écarté : la serre bâtie bloc par bloc puis validée (Forestry, Firmalife).

### 2.2 Paliers

| Palier | Volume (l×h×p) | Parcelles | Emplacements de module | Densité max |
|---|---|---|---|---|
| P1 | 3×3×3 | 4 (2×2) | 2 | ×2 |
| P2 | 5×4×5 | 9 (3×3) | 4 | ×3 |
| P3 | 7×5×7 | 16 (4×4) | 6 | ×4 |

Un palier ne donne **jamais de vitesse**. Il donne de la place : plus de parcelles et de modules.

### 2.3 Monter de palier

On utilise l'item du palier suivant **sur une serre posée**. Le contour fantôme montre le nouveau
volume, centré sur le même maître. Si la place manque, il est rouge et rien ne change. Sinon, le
volume s'agrandit en conservant parcelles, modules et réserves, et l'item est consommé.

Il n'est pas possible de redescendre : on casse la serre et on repose un palier inférieur.

---

## 3. Parcelle

### 3.1 Contenu

| Emplacement | Obligatoire | Rôle |
|---|---|---|
| **Culture** | oui | la graine ou le plant |
| **Substrat** | oui | ce dans quoi elle pousse : terre, sable, sable des âmes, terre moddée… |
| **Catalyseur** | non | le bloc sous le substrat (le crux de Mystical Agriculture, par exemple) |

Les trois sont **permanents**, comme dans Botany Pots : une graine posée replante à l'infini.
Les graines produites en plus sortent avec la récolte.

### 3.2 État

Pour chaque parcelle : progression de 0 à 1, densité, statut. La croissance est **simulée** : la
parcelle n'est pas un vrai bloc du monde et ne reçoit pas de *random ticks*.

### 3.3 Densité

Une parcelle densifiée représente N plants. **Tout** est multiplié par N : la récolte, l'eau,
l'engrais, les résidus et l'énergie des modules. Le joueur gagne de la place et de la
simplicité, pas des ressources gratuites. Le plafond dépend du palier (§2.2).

Écarté : un « parallélisme » qui multiplie la sortie sans multiplier le coût. C'était un module de
vitesse déguisé, qui aurait rendu les paliers inutiles.

### 3.4 Plusieurs cultures

Chaque parcelle a sa propre culture, et une même serre peut en mélanger. Comme la serre n'a
qu'**un seul environnement**, mélanger une culture du chaud et une du froid oblige à un compromis.
C'est un choix intéressant pour le joueur, sans ajouter de règle.

Plus tard : les bonus entre plantes voisines.

---

## 4. Environnement

### 4.1 Les trois facteurs

| Facteur | Valeur de base (le lieu) | Corrigé par |
|---|---|---|
| **Lumière** | lumière du ciel au maître, qui suit le cycle jour/nuit ; 0 sans ciel visible | Lampe de croissance |
| **Eau** | ce que contient la réserve, rapporté à la consommation | Collecteur de pluie, tuyau branché au socle |
| **Température** | température du biome au maître, convertie en °C | Chauffage / Refroidisseur |

Le climat est **une valeur de la serre entière**, pas un calcul par bloc. On le recalcule rarement
(§11), jamais à chaque tick.

Écartés : l'humidité, qui ferait doublon avec l'eau, et la dimension. Le Nether devient
simplement « très chaud » : la verrue du Nether pousse dans l'Overworld si on la chauffe.

### 4.2 Exigences

Chaque culture déclare une plage idéale par facteur :

```json
{ "min_light": 9, "min_temperature": 15, "max_temperature": 30 }
```

L'eau n'est pas une exigence par culture, mais un **coût**. Chaque culture a le sien, que le JSON
peut changer : `ticks` (durée d'un cycle, 28 672 par défaut, un blé vanilla idéal), `water_per_cycle`
(500 mB) et `energy_per_tick` (10 FE/t, payés seulement quand la parcelle est alimentée). Chaque
parcelle paie sa culture ; la serre affiche la somme.

La serre travaille **tous les 10 ticks**. Une parcelle récolte au plus une fois par pas : si sa
culture a fini plusieurs cycles entre-temps, elle les récolte en un lot (64 au plus), la table de
loot étant tirée pour 8 plants puis mise à l'échelle. Un lot qui ne tient pas dans la sortie est
coupé en deux, jusqu'à un seul cycle ; le reste attend, mûr.

- **Cultures vanilla** : des valeurs livrées avec le mod (canne à sucre et cactus aiment le chaud,
  baies sucrées le froid, verrue du Nether le très chaud…).
- **Cultures moddées détectées automatiquement** : exigence **tolérante** par défaut (large plage
  de température, lumière 9 comme en vanilla), qu'un JSON peut corriger.

### 4.3 Calcul (loi du minimum)

Chaque facteur donne une efficacité entre `0.1` et `1.0`. Elle vaut 1.0 dans la plage idéale et
baisse linéairement avec l'écart, sans descendre sous le plancher de 0.1. La vitesse de la
parcelle vaut :

```
vitesse = vitesse_de_base × min(eff_lumière, eff_eau, eff_température) × modules
```

Seul le **pire** facteur compte : l'écran l'affiche en une ligne, par exemple
`Limité par : température — 12 °C (idéal 15–30)`.

Le plancher de 0.1 tient la promesse : un écart **ralentit**, il ne bloque jamais. Exception :
quand la réserve d'eau est vide, l'efficacité de l'eau tombe à 0. C'est une ressource qui manque,
pas un écart d'environnement, et le statut le dit (§9.2).

Écarté : multiplier les pénalités de chaque facteur. Trois petits écarts donnaient un résultat que
personne ne savait expliquer.

---

## 5. Cultures : détection et compatibilité

### 5.1 Détection automatique

Au chargement des données, pour chaque item de graine candidat :

1. Trouver le bloc qu'il pose (`BlockItem` → bloc de culture).
2. Pour chaque substrat candidat, poser **virtuellement** la culture dessus, dans un mini-monde
   fictif (un `BlockGetter` de quelques blocs), et demander `canSurvive`. On obtient ainsi la
   liste des substrats acceptés.
3. Obtenir la récolte en **simulant la table de loot** de l'état mûr.

Ce résultat est mis en cache par rechargement de datapack et envoyé au client (network codec), pour
que l'interface et JEI n'aient pas besoin de demander au serveur.

### 5.2 Surcharges JSON

Fichiers `data/<ns>/yafs/crop/*.json`, relus à chaque `/reload` et envoyés aux joueurs. Chaque
champ présent **remplace** ce que la détection a trouvé ; un champ absent le laisse :

```json
{
  "seed": "mysticalagriculture:nether_star_seeds",
  "substrates": ["#minecraft:dirt", "minecraft:farmland"],
  "catalyst": "mysticalagriculture:nether_star_crux",
  "requirements": { "min_temperature": 25, "max_temperature": 40 }
}
```

Le résidu (`residue`) s'y ajoutera avec YAF-023.

Une entrée est obligatoire pour les **cas exotiques**, chacun couvert par un GameTest :

- la canne à sucre et le cactus, qui poussent en hauteur ;
- les cultures sur 2 blocs (comme la plante carnivore) ;
- les plantes avec un bloc-entité ;
- les plantes qui vérifient leur sol pendant la croissance plutôt qu'avec `canSurvive` (le crux
  de Mystical Agriculture).

Un JSON invalide est **refusé et signalé dans le journal**, jamais corrigé en silence : un `Codec`
borné, comme dans FactoryIO.

### 5.3 Substrats

Fichiers `data/<ns>/yafs/substrate/*.json` : quels items comptent comme quel bloc de sol. La terre
compte comme une terre labourée irriguée, pour éviter de manipuler des houes :

```json
{ "items": ["minecraft:dirt", "minecraft:grass_block"], "counts_as": "minecraft:farmland" }
``` Un substrat peut aussi porter un bonus,
par exemple la « terre fertile » qui donne +rendement. C'est une progression sans passer par les
modules.

---

## 6. Ressources

| Ressource | Statut | Détail |
|---|---|---|
| **Eau** | obligatoire | réserve interne en fluide ; consommée à chaque cycle de croissance, × densité |
| **Engrais** | facultatif | poudre d'os, compost, engrais typés : vitesse **ou** rendement, à la Stardew |
| **Énergie (FE)** | modules seulement | une serre au soleil et arrosée tourne sans énergie |

Le mod **ne produit pas d'énergie**, comme FactoryIO.

### 6.1 Résidus

À chaque récolte, une culture peut donner un **Résidu**. Par défaut, c'est du **compost**, avec
une probabilité définie par culture. Le compost **se recycle en engrais** : à la main, ou
automatiquement avec le module Composteur. La boucle est fermée.

Plus tard : des résidus fluides (une « boue »), qui sortiraient par le socle.

---

## 7. Modules

**Un item par module**, comme un bloc par palier (`yafs:heater`, `yafs:grow_lamp`…). Ce que fait un
module vient de la définition de même identifiant dans le registre datapack `yafs/module` : un
modpack retouche les chiffres d'un module sans code, mais un nouveau module demande un item.

Écarté : un seul item `yafs:module` à composant, sur le modèle des augments de Portable Beacons. Un
modpack aurait pu ajouter un module sans code, mais des items distincts se lisent mieux dans JEI,
les recettes et l'inventaire.

| Module | Effet | Coût | Cumulable |
|---|---|---|---|
| Lampe de croissance | + lumière | FE | non |
| Chauffage | + température | FE | non |
| Refroidisseur | − température | FE | non |
| Collecteur de pluie | + eau quand il pleut, ciel visible requis | — | non |
| Accélérateur | + vitesse | FE + eau, proportionnels | oui, coût croissant |
| Composteur | résidus → engrais | — | non |
| Capacité | piles plus grandes dans les réserves | — | non |
| Densité | augmente la densité des parcelles jusqu'au plafond du palier | tout × densité | non |

**Règle du mod : aucun bonus n'est gratuit.** Tout ce qui accélère multiplie aussi ce qu'il
consomme.

**Énergie et augments** (décidé le 30/10/2026). Une parcelle qui a son énergie pour le pas pousse
plus vite (×2, réglable en config) pour un coût de base par parcelle (10 FE/t, réglable). Les
modules d'augment **multiplient** : la vitesse (Accélérateur I/II/III : ×2/×4/×8), et en même temps
l'énergie (×4/×16/×64) et l'eau (×2/×4/×8). Trois accélérateurs et le bonus de base font ×128 : on
peut aller absurdement vite, à un prix absurde. La densité (×2/×3/×4 plants, bornée par le palier)
multiplie récolte, eau et énergie. Sans énergie, vitesse 1 et accélérateurs à l'arrêt.

---

## 8. Le socle

La couche du bas de la serre est un bloc plein, façon béton, et **chacun de ses blocs se branche
de partout** : l'eau et l'énergie y entrent, la récolte en sort. Pas d'entrée d'items : graines,
sols et catalyseurs se posent une fois, à la main, et restent ; une entrée reviendra avec ce qui se
consomme en continu (l'engrais). Les murs au-dessus du socle ne laissent rien passer. Un seau
marche sur n'importe quel bloc.

Écarté (30/10/2026) : un item « Port » qui spécialisait un bloc (sortie filtrée, entrée d'eau,
entrée d'énergie), plafonné par palier. Le socle prend déjà tout ; trier la récolte, les mods de
logistique le font très bien.

Le socle délègue au maître ses handlers tels quels, sans les envelopper : inserters de FactoryIO,
tuyaux et mods tiers voient un inventaire ordinaire.

**Rien n'est jamais détruit** : quand la sortie ou la réserve de résidus est pleine, la parcelle
reste mûre et passe au statut BLOCKED.

---

## 9. Interface

Même famille visuelle que FactoryIO et Portable Beacons : on recopie et on porte `GuiMetrics`,
`GuiTheme` et `GuiSprites`.

### 9.1 Disposition

```
┌──────────────────────────────────────┐
│ Serre III                       ●    │ ← titre, voyant d'état
│ ▐█▌  ┌──┬──┬──┬──┐                   │
│ ▐█▌  │🌾│🥕│🌾│🥔│   colonne 0 :      │
│ ▐█▌  ├──┼──┼──┼──┤   jauge d'eau      │
│ ▐█▌  │🌾│  │🎃│🌵│   (+ FE si un      │
│ ▐░▌  ├──┼──┼──┼──┤   module en        │
│ ▐░▌  │…  grille 4×4 de parcelles…│    │   consomme)
│      └──┴──┴──┴──┘                   │
│ inventaire du joueur + hotbar        │
└──────────────────────────────────────┘
 [onglet jaune ◀ Environnement]    [Modules ▶ onglet bleu]
```

- **Case de parcelle** : icône de la culture, barre de croissance, liseré de la couleur du statut,
  badge de densité (×2…). Une case vide affiche `+`.
- **Onglet jaune, à gauche** : les trois facteurs (valeur actuelle / plage idéale), la ligne
  « Limité par », la production en items/min et la consommation d'eau par minute.
- **Onglet bleu, à droite** : les emplacements de modules. Comme dans Portable Beacons, l'effet
  d'un module est recalculé en direct pour que le joueur le voie avant de fermer.
- **Clic sur une parcelle** : une fenêtre superposée avec les emplacements Culture / Substrat /
  Catalyseur et les exigences de la culture comparées à l'environnement actuel. Les entrées
  bloquées restent **affichées et grisées**, avec la raison.

### 9.2 Statuts

Ce sont les cinq statuts de `GuiTheme.Status`, appliqués à la serre comme à chaque parcelle :

| Statut | Cas |
|---|---|
| WORKING | pousse |
| WAITING | parcelle vide, ou pas de lumière la nuit sans lampe |
| BLOCKED | sortie ou réserve de résidus pleine |
| PROBLEM | eau à zéro, substrat incompatible, catalyseur manquant |
| OFF | éteinte (redstone ou interrupteur) |

Le statut d'une serre est celui de sa parcelle **la plus grave**. Une couleur ne sert qu'à une
chose : le rouge n'est jamais décoratif.

### 9.3 Synchronisation

Les valeurs affichées sont calculées **côté serveur** et transmises par `ContainerData`, seulement
quand le menu est ouvert. Les boutons passent par `clickMenuButton(id)`. Aucun paquet périodique
du serveur vers le client.

---

## 10. Rendu

Le maître dessine les plantes de toutes les parcelles, stade par stade, avec les modèles baked de
l'état de chaque culture. C'est la promesse n°1 du mod, et aussi son **risque n°1**.

- Le niveau de détail baisse avec la distance : au-delà d'environ 32 blocs, on ne montre que le
  dernier stade figé, et au-delà d'environ 64 blocs, plus rien (à mesurer).
- Le client ne reçoit la progression d'une parcelle que lorsque son **stade** change, jamais à
  chaque tick.
- La boîte de rendu (`getRenderBoundingBox`) couvre tout le volume.

C'est le premier chantier à mesurer, avec un budget versionné comme dans `FactoryIO/docs/10`.

---

## 11. Équilibre et performance

- **Vitesse de base** : une parcelle à 1.0 d'efficacité pousse comme une culture vanilla bien
  éclairée, en moyenne, sur de la terre labourée irriguée. Le gain de la serre vient du
  parallélisme, de l'automatisation et du fait de cultiver n'importe où, **pas** d'un
  multiplicateur.
- **Chunk non chargé** : rien ne pousse, pas de rattrapage au rechargement. Le rattrapage
  fausserait l'équilibre et risquerait la duplication en multijoueur.
- **Ticks** : la serre avance par pas de 20 ticks. L'environnement de base (biome, ciel, pluie)
  est recalculé toutes les 100 ticks et à chaque changement de module. Pas de `stream()` ni
  d'allocation dans `tick()`.
- **Config serveur** : un multiplicateur global de vitesse, les résidus activables ou non, le
  plafond de densité.

---

## 12. Progression et recettes

Toutes les recettes sont en datapack.

| Palier | Moment de la partie | Ingrédients de départ |
|---|---|---|
| P1 | début | verre, bois, fer ; aucune énergie, marche au soleil avec le Collecteur de pluie |
| P2 | milieu | or, redstone, un composant qui demande de l'énergie |
| P3 | fin | diamant, plus un item **issu d'une culture de serre** |

---

## 13. Architecture technique

- `core/` : la logique pure (efficacité, loi du minimum, densité, coûts, résolution d'une
  culture). Aucune référence au jeu ; elle est testée en JUnit.
- `content/` : la serre, les parcelles, les modules, le socle, et le cadre multibloc porté de
  FactoryIO.
- `client/` : les écrans, le rendu, l'aperçu de pose. C'est le seul endroit où
  `net.minecraft.client` est autorisé.
- Registres datapack avec network codec : `crop`, `substrate`, `module`, `fertilizer`.
- **Code de FactoryIO** : recopié et porté (charte GUI, cadre multibloc), pas partagé en
  bibliothèque. On en extraira une le jour où deux mods sur la même version auront besoin du même
  correctif.
- **Port 26.x** : la logique de `core/` ne bouge pas ; seule la plateforme se réécrit (voir
  `BeaconPack/PORTING.md`).

### Tests

| Type | Couvre |
|---|---|
| JUnit | codecs, calcul d'efficacité, loi du minimum, densité, coûts |
| GameTest | pose, montée de palier, casse avec conservation du contenu, socle, chaque cas exotique de §5.2, persistance au rechargement |

Les invariants vérifiés en permanence : **aucun item n'est créé ni détruit** en dehors de la
récolte, persistance après rechargement, budgets de performance.

---

## 14. Intégrations

Toutes sont des dépendances facultatives, déclarées `compileOnly`, et protégées comme dans
Portable Beacons : le mod tourne sans aucune d'elles.

| Mod | Version du mod | Contenu |
|---|---|---|
| JEI | première | pages cultures : substrats, catalyseur, exigences, récolte |
| Jade | première | statut et facteur limitant au survol |
| Mystical Agriculture | première | JSON de surcharge pour les cruxes |
| Serene Seasons | deuxième | la saison décale la température de base |
| Agricraft | deuxième | les statistiques de la graine modifient vitesse et rendement |

---

## 15. Hors de la première version

Explicitement exclus de la première version : les arbres, les champignons, les cultures aquatiques,
les animaux et mobs simulés, les bonus entre plantes voisines, les résidus fluides, le rattrapage
hors chargement et la génétique. Le modèle de **Culture** est prévu pour accueillir les arbres
ensuite. Les animaux et les mobs demanderont leur propre conception.
