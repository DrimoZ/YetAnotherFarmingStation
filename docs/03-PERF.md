# 03 — Budget de performance (YAF-041)

Le banc : 50 serres P3 pleines (16 parcelles de blé, densité ×4, quatre Accélérateurs III, une
lampe), en ligne de mire. On le relance après tout changement du tick ou du rendu :

```
YAFS_PERF_BENCH=1 ./gradlew runClient
```

Il écrit `run/perf/bench.txt` et ferme le jeu (code : `gametest/PerfBench.java`).

## Budget

| Mesure | Budget | Mesuré le 31/10/2026 |
|---|---|---|
| Serveur, par serre P3 et par tick | ≤ 20 µs | 5,8 µs |
| Serveur, 50 serres P3 | ≤ 2 % d'un tick (1 ms) | 0,29 ms (0,6 %) |
| Rendu, temps CPU par image des 50 serres | ≤ 0,5 ms | 0,14 ms |

Les mesures varient de ±10 % d'un lancement à l'autre ; le budget laisse de la marge exprès.

## Ce qui les tient

- **Serveur** : la serre travaille tous les 10 ticks, décalée selon sa position ; entre deux pas,
  elle ne fait que prélever son énergie. Une récolte de plusieurs cycles tire la table de loot pour
  8 plants au plus, puis met à l'échelle. Le signal redstone n'est relu qu'au changement d'un voisin.
- **Rendu** : les plantes sont cuites dans le maillage du chunk (`GreenhouseModel`), pas redessinées
  à chaque image. Le chunk se reconstruit quand un stade visible change (8 par croissance). Avant :
  4,9 ms par image pour 1 408 modèles redessinés à chaque image.

## Limites connues

- Une serre très accélérée change de stade souvent : chaque changement reconstruit sa section de
  chunk, sur les threads de construction. À surveiller si un joueur aligne des dizaines de serres
  à cycle de quelques secondes.
- Le nombre d'images par seconde n'a pas bougé entre avant et après (45 → 46 sur la machine de
  mesure) : il était limité ailleurs. Le gain est du temps CPU rendu au reste du jeu.
