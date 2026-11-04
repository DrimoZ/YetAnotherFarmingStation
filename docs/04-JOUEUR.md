# 04 — Guide du joueur

Ce guide explique comment jouer avec la serre. Les tableaux entre balises `gen:` viennent des
fichiers de données et du code : ils sont réécrits par `java tools/GenGuide.java`, et
`java tools/GenGuide.java --check` échoue quand l'un d'eux est en retard. Ne les modifiez pas à
la main.

## 1. Poser sa serre

Fabriquez une **Serre (palier I)** : du verre tout autour, un lingot de fer au centre, des planches
dessous. Posez-la au sol : tout le volume se forme d'un geste, sans rien construire. Un contour
fantôme montre où elle ira, vert si la place est libre, rouge sinon.

Casser n'importe quel bloc de la serre casse tout le volume : la serre revient en item, et son
contenu (graines, sorties, modules, engrais) tombe au sol.

### Les paliers

Un palier donne de la place, jamais de vitesse.

<!-- gen:tiers -->
| Palier | Volume (largeur × hauteur × profondeur) | Parcelles | Emplacements de module |
|---|---|---|---|
| I | 3 × 3 × 3 | 4 (2 × 2) | 2 |
| II | 5 × 4 × 5 | 9 (3 × 3) | 4 |
| III | 7 × 5 × 7 | 16 (4 × 4) | 6 |
<!-- /gen:tiers -->

**Monter de palier** : utilisez l'item du palier suivant sur la serre posée. Le contour montre le
nouveau volume, sur le même centre ; s'il manque de la place, il est rouge et rien ne change.
Sinon la serre grandit, garde tout son contenu, et l'item est consommé. La **Serre (palier II)**
se fabrique avec du verre, de l'or, de la redstone et un bloc de fer ; la **Serre (palier III)**
avec du verre, du diamant, des tranches de melon scintillant et un bloc d'or.

## 2. Le socle : où tout se branche

La couche du bas est le **socle**, un bloc plein de la serre. Chacun de ses blocs se branche
**de tous côtés** : tuyaux, câbles et entonnoirs y trouvent :

- l'**eau** et l'**énergie**, qui y entrent ;
- l'**engrais**, qui entre dans son emplacement ;
- la **récolte**, qui en sort.

Rien d'autre n'entre : graines, sols et catalyseurs se posent à la main, une fois, et restent. Les
murs au-dessus du socle ne laissent rien passer. Un seau fonctionne sur n'importe quel bloc de la
serre : il remplit ou vide le réservoir.

## 3. L'écran

Un clic droit sur la serre l'ouvre, même avec un item en main (sauf un seau, qui sert au réservoir,
et l'item du palier suivant, qui agrandit la serre).

| Où | Quoi |
|---|---|
| Au milieu | les **parcelles**, la jauge d'**énergie** à gauche, la jauge d'**eau** et l'emplacement d'**engrais** à droite des parcelles, la **sortie** (la récolte) |
| Onglet **Environnement** (à gauche) | la lumière, la température et la présence d'eau, puis « Conditions idéales » ou ce qui limite la parcelle la plus lente |
| Onglet **Fonctionnement** (à gauche) | ce que la serre consomme par tick (énergie, eau, engrais), sa production (vitesse totale, rendement, plants par parcelle), la taille d'une case de sortie, et combien de parcelles poussent. Survolez la ligne d'énergie pour voir d'où vient le besoin |
| Onglet **Modules** (à droite) | les emplacements de modules et, pour chaque module posé, combien vous en avez sur combien la serre en accepte |
| Onglet **Contrôle** (à droite) | l'interrupteur et la condition redstone |

Chaque onglet se replie en cliquant son en-tête. Un clic sur une parcelle ouvre son **détail** :
trois emplacements (culture, substrat, catalyseur), ce que la culture exige, où elle en est, et la
raison, en clair, de ce qui la bloque.

## 4. Planter

Chaque parcelle a trois emplacements :

1. la **culture** : une graine. Toute plante qui pousse par stades (blé, carotte, pomme de terre,
   betterave, verrue du Nether, et celles des autres mods) est reconnue automatiquement ;
2. le **substrat** : le sol où elle pousse, obligatoire. La terre compte comme de la terre labourée
   irriguée, sans houe ;
3. le **catalyseur**, seulement pour les cultures qui en exigent un (le crux de Mystical
   Agriculture, par exemple).

Le **maj-clic** range pour vous : parcelle ouverte, l'item va dans la bonne case ; sinon, il se
répartit à raison d'un par parcelle (une graine dans chaque parcelle vide, un sol ou un
catalyseur dans chaque parcelle semée qui en veut).

### Ce qu'une culture exige

Chaque culture attend de la lumière et une plage de température. Sans réglage, une culture
reconnue automatiquement est tolérante ; ces cultures-ci ont leur propre fichier :

<!-- gen:crops -->
| Culture réglée par un fichier | Lumière | Température | Particularité |
|---|---|---|---|
| Graines de betterave | 9 à 15 | 0 à 22 °C | — |
| Cactus | 9 à 15 | 25 à 50 °C | — |
| Carotte | 9 à 15 | 5 à 25 °C | — |
| Fèves de cacao | 9 à 15 | 20 à 35 °C | — |
| Graines de pastèque | 9 à 15 | 15 à 35 °C | récolte du bloc Pastèque |
| Verrue du Nether | 0 à 15 | 35 à 60 °C | — |
| `minecraft:pitcher_pod` | 9 à 15 | 15 à 30 °C | récolte du bloc `minecraft:pitcher_plant` |
| Pomme de terre | 9 à 15 | 5 à 25 °C | — |
| Graines de citrouille | 9 à 15 | 10 à 30 °C | récolte du bloc Citrouille |
| Canne à sucre | 9 à 15 | 20 à 40 °C | — |
| Baies sucrées | 9 à 15 | -10 à 14 °C | — |
| `minecraft:torchflower_seeds` | 9 à 15 | 15 à 35 °C | — |
| Graines de blé | 9 à 15 | 10 à 30 °C | — |
<!-- /gen:crops -->

L'efficacité d'une parcelle est la **plus faible** de trois facteurs : la lumière, l'eau, la
température. Le facteur qui limite s'affiche (« Limité par la lumière (60 %) ») ; corrigez-le et la
parcelle repart. Une culture hors de sa plage ralentit, elle ne meurt jamais.

### Les valeurs de base

<!-- gen:constants -->
| Ce que c'est | Valeur |
|---|---|
| Réservoir d'eau de la serre | 16 000 mB |
| Réserve d'énergie de base | 1 000 000 FE |
| Une culture, sans réglage : durée d'un cycle | 28 672 ticks, environ 24 min |
| Une culture, sans réglage : eau par cycle et par plant | 500 mB |
| Une culture, sans réglage : énergie, si la parcelle est alimentée | 10 FE/t |
| Chance de résidu, par plant et par cycle | 0,2 |
| Une culture reconnue automatiquement : lumière | 9 à 15 |
| Une culture reconnue automatiquement : température | 0 à 35 °C |
| Items qu'une case de sortie tient, sans module de capacité | 64 |
| Modules d'une même sorte acceptés, par défaut | 16 |
| La serre travaille tous les | 10 ticks |
| Réglage `energizedSpeed` (vitesse d'une parcelle alimentée) | ×2 |
| Réglage `growthSpeed` (multiplicateur global) | ×1 |
| Réglage `residues` | activés |
<!-- /gen:constants -->

## 5. L'eau et l'énergie

**L'eau** se boit à chaque tick, à mesure que ça pousse. Remplissez le réservoir au seau, ou par un
tuyau au socle ; un module collecte aussi la pluie. Quand l'eau manque, la croissance ralentit au
lieu de s'arrêter, et l'écran dit « Limité par l'eau ».

**L'énergie** n'est utile qu'à ceux qui la demandent. Une serre au soleil et arrosée tourne sans
énergie. Une parcelle **alimentée** pousse plus vite ; les modules qui consomment (lampe, chauffage,
refroidisseur, obscurité) et ceux qui accélèrent en ont besoin. L'énergie est prélevée **à chaque
tick**, tout ou rien : si la réserve ne couvre pas un tick, le bonus de ce tick n'a pas lieu, et
l'onglet Fonctionnement passe le besoin en rouge, avec « Énergie insuffisante » ; survolez-le pour
voir ce qui manque. Le mod ne produit pas d'énergie : branchez-la au socle.

Plus une parcelle est accélérée, moins un cycle boit d'eau (l'eau par cycle suit la racine de la
vitesse) : accélérer reste possible sans se noyer dans les tuyaux.

## 6. Les modules

Un module se pose dans un emplacement de l'onglet Modules. Une serre accepte plusieurs modules
d'une même sorte, jusqu'à son maximum ; **leurs bonus s'additionnent** (deux accélérateurs I
valent +1 chacun sur la vitesse). Des modules de sortes différentes, eux, se multiplient entre
eux. La lampe et l'obscurité s'excluent.

<!-- gen:modules -->
| Module | Ce qu'il fait | Ce qu'il coûte | Maximum |
|---|---|---|---|
| Lampe de croissance | lumière jamais sous 15 | 20 FE/t | 1 |
| Obscurité | lumière plafonnée à 0 | 20 FE/t | 1 |
| Chauffage | température +20 °C | 40 FE/t | 16 |
| Refroidisseur | température -20 °C | 40 FE/t | 16 |
| Collecteur de pluie | 25 mB d'eau de pluie par seconde | — | 16 |
| Accélérateur I | vitesse des parcelles alimentées ×2 (les bonus s'additionnent) | énergie des parcelles ×4 | 16 |
| Accélérateur II | vitesse des parcelles alimentées ×4 (les bonus s'additionnent) | énergie des parcelles ×16 | 16 |
| Accélérateur III | vitesse des parcelles alimentées ×8 (les bonus s'additionnent) | énergie des parcelles ×64 | 16 |
| Densité I | +1 plant par parcelle | chaque plant boit et consomme | 16 |
| Densité II | +2 plants par parcelle | chaque plant boit et consomme | 16 |
| Densité III | +3 plants par parcelle | chaque plant boit et consomme | 16 |
| Batterie | +1 000 000 FE de réserve | — | 64 |
| Capacité | +64 items par case de sortie | — | 64 |
| Composteur | change 4 composts de la sortie en un engrais, 1 lot par pas | — | 4 |
<!-- /gen:modules -->

Aucun bonus n'est gratuit : tout ce qui accélère coûte de l'énergie, et la densité multiplie
l'eau et l'énergie autant que la récolte.

## 7. Engrais, résidus et compost

Une récolte laisse parfois du **compost**, avec une chance par plant et par cycle. Quatre composts
font un **Engrais**, à l'établi ou avec le module Composteur. La boucle n'est pas gratuite : le
compost suit ce que la serre produit, l'engrais suit le nombre de parcelles. Une petite serre ne
couvre qu'une toute petite part de son besoin ; une serre poussée à fond en produit plus qu'elle
n'en use.

L'**emplacement d'engrais** est sous la jauge d'eau. Il prend **une seule sorte à la fois**, qui
s'applique à toutes les parcelles qui poussent : elle accélère **ou** elle augmente la récolte.

<!-- gen:fertilizers -->
| Engrais | Effet sur toute la serre | Un item nourrit une parcelle |
|---|---|---|
| Poudre d'os | croissance ×1,5 | 5 min |
| Engrais | récolte ×1,5 | 10 min |
<!-- /gen:fertilizers -->

L'engrais s'use **par parcelle qui pousse et par temps**, jamais par plant ni par vitesse : une
serre accélérée ou dense n'en consomme pas plus. Une serre vide, éteinte ou à sec n'en use pas. Le
nombre d'items par minute s'affiche dans Fonctionnement.

## 8. Les sols

Le sol d'une parcelle peut rendre plus, **pour cette parcelle seule**. Ce bonus se cumule avec
l'engrais, qui est pour toute la serre. Survolez un sol : son bonus est dans son infobulle.

<!-- gen:soils -->
| Sols | Comptent comme | Bonus de leur parcelle |
|---|---|---|
| Terre fertile | Terre labourée | récolte ×1,25 |
| Terre, Bloc d'herbe, Chemin de terre | Terre labourée | — |
<!-- /gen:soils -->

## 9. Contrôle par redstone

L'onglet Contrôle porte l'**interrupteur**, qui l'emporte sur tout : éteinte, la serre ne répond à
aucun signal. Allumée, la **condition redstone** décide : toujours active, active si le signal est
inférieur au seuil, ou s'il lui est supérieur ou égal. Le signal pris en compte est le plus fort qui
atteint n'importe quel bloc de la serre (de 0 à 15). Par défaut, la serre s'arrête dès qu'elle
reçoit un signal.

## 10. Compatibilité

- **JEI** : une catégorie « Cultures de serre », une page par culture (graine, sols, catalyseur,
  récolte, exigences, durée, eau, énergie), avec les trois serres comme machines.
- **Jade** : au survol d'un bloc de la serre, son état, les parcelles qui poussent, ce qui les
  limite et l'énergie demandée.
- **Mystical Agriculture** : chaque graine à crux exige son crux comme catalyseur.

## 11. Pour les modpacks

Tout se règle par datapack ou par la configuration serveur, sans toucher au mod : cultures (durée,
eau, énergie, résidu, catalyseur, graine, exigences), sols et leurs bonus, engrais, modules
(effets, coûts, maximum, Composteur) et recettes. Partout où un champ désigne des items, il accepte
un item ou un `#tag`. Le détail champ par champ est dans
[`01-DESIGN.md`](01-DESIGN.md), section « Tout se règle par datapack ».

La configuration serveur (`yafs-server.toml`) a trois réglages : `energizedSpeed`, `growthSpeed`
(un multiplicateur global de la croissance) et `residues`. Leurs valeurs par défaut sont dans le
tableau des valeurs de base.
