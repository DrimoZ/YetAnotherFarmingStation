# YetAnotherFarmingStation

Un mod où l'on fait pousser des cultures dans une serre multibloc, et où l'environnement de la
serre décide de la vitesse de croissance, expliquée au joueur.

## Langage

### Structure

**Serre** :
Le multibloc, posé d'un seul geste à partir d'un item, jamais construit bloc par bloc
(voir `docs/adr/0001`).
_Éviter_ : greenhouse, ferme, farming station, machine

**Palier** :
Le niveau d'une serre ; il fixe sa taille, son nombre de parcelles et de modules, jamais sa
vitesse. On monte de palier sur place.
_Éviter_ : tier, niveau, rang

**Socle** :
La couche du bas d'une serre, un bloc plein ; tout s'y branche, de tous côtés. Les murs au-dessus
ne laissent rien passer.
_Éviter_ : port, face, valve, hatch, bus, interface

**Module** :
Un item placé dans une serre qui corrige son environnement ou accélère ses parcelles, à un coût.
_Éviter_ : upgrade, augment, amélioration, addon

**Densité** :
Le nombre de plants qu'une parcelle représente : un, plus ce qu'ajoutent les modules de densité ;
tout ce qu'elle produit et consomme est multiplié d'autant.
_Éviter_ : parallélisme, multiplicateur, stack, batch

**Parcelle** :
Un emplacement de culture dans une serre, qui évolue indépendamment des autres.
_Éviter_ : slot, pot, case, bac

### Culture

**Culture** :
Ce qui pousse dans une parcelle et produit des items en mûrissant ; dans la première version,
seulement des plantes des champs.
_Éviter_ : crop, plante, graine

**Substrat** :
Ce dans quoi la culture d'une parcelle est plantée ; obligatoire, permanent, et peut porter un
bonus.
_Éviter_ : sol, soil, terre

**Catalyseur** :
Un bloc facultatif placé sous le substrat, que certaines cultures exigent pour pousser.
_Éviter_ : crux, socle, bloc requis

**Résidu** :
Un sous-produit qu'une récolte donne parfois, en plus de la production.
_Éviter_ : déchet, sludge, boue, byproduct

**Engrais** :
Ce qu'on met dans l'emplacement dédié de la serre pour accélérer toutes ses parcelles, ou leur faire rendre plus ; consommé à la mesure de ce qui pousse.
_Éviter_ : fertilisant, booster

**Environnement** :
Les quelques grandeurs que la serre fournit à ses parcelles (lumière, eau, température) :
celles du lieu où elle est posée, corrigées par ses modules.
_Éviter_ : climat, conditions

**Exigence** :
Ce qu'une culture attend de l'environnement ; un écart ralentit la croissance sans jamais la
bloquer.
_Éviter_ : besoin, prérequis, condition
