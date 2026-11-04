# Yet Another Farming Station

Une serre multibloc pour NeoForge **1.21.1**, posée d'un geste, où l'environnement décide de la
croissance. Le nom est ironique, à dessein : dans le jeu, on dit *serre*, jamais « station ».

Posez la serre, plantez, branchez de l'eau : elle fait pousser toute plante qui pousse par stades,
vanilla ou moddée, et vous rend la récolte à sa sortie. Lumière, température et eau décident de la
vitesse ; l'énergie, les modules, l'engrais et le sol la poussent plus loin, chacun avec son prix.

## Ce que ça apporte

- **Posée, pas construite.** Un item, un clic : tout le volume se forme. Trois paliers qui donnent
  de la place, jamais de vitesse ; on monte de palier sur place, sans rien perdre.
- **Toute culture reconnue.** La détection essaie chaque graine sur un sol virtuel : les cultures
  d'autres mods marchent sans configuration, et un fichier peut corriger celles qui coincent.
- **Un environnement lisible.** La lumière, la température et l'eau se lisent sur l'écran, avec
  la raison, en clair, de ce qui bloque une parcelle.
- **Des modules qui se cumulent.** Accélérateurs, densité, lampe, chauffage, refroidisseur, extinction, collecteur de pluie, batterie, capacité de sortie,
  composteur. Aucun bonus n'est gratuit.
- **Une boucle de ressources.** Les récoltes laissent du compost, qui devient de l'engrais.
- **Un socle qui se branche de partout.** Eau, énergie et engrais y entrent, la récolte en sort.
- **Tout se règle par données.** Cultures, sols, engrais et modules sont des fichiers de
  datapack ; partout où l'on désigne des items, un `#tag` convient.

Le guide complet, avec toutes les valeurs, est dans [`docs/04-JOUEUR.md`](docs/04-JOUEUR.md).

## Compatibilité

Facultatives, jamais requises : **JEI** (une page par culture), **Jade** (l'état de la serre au
survol), **Mystical Agriculture** (chaque graine à crux exige son crux). Le mod tourne pareil sans
elles.

## Développer

Java 21, Gradle 9, ModDevGradle. Les commandes, les règles de code et le vocabulaire sont dans
[`CLAUDE.md`](CLAUDE.md) et [`CONTEXT.md`](CONTEXT.md).

```
gradlew build              compile et lance les tests JUnit
gradlew runGameTestServer  les GameTests, sans interface
gradlew runClient          un client de développement
java tools/GenGuide.java   régénère les tableaux du guide joueur depuis les données
```

La spécification est dans [`docs/01-DESIGN.md`](docs/01-DESIGN.md), les tickets dans
[`docs/02-BACKLOG.md`](docs/02-BACKLOG.md), le budget de performance dans
[`docs/03-PERF.md`](docs/03-PERF.md), le texte des pages de store dans
[`docs/05-STORE.md`](docs/05-STORE.md).

## Licence

Code sous licence MIT ([`LICENSE`](LICENSE)). Les assets sont tous droits réservés
([`LICENSE-ASSETS`](LICENSE-ASSETS)).
