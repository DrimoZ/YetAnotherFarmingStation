# La serre est posée d'un geste, pas construite

Les serres de référence (Forestry, Firmalife) se bâtissent bloc par bloc puis sont validées ; on
a choisi l'inverse : un item pose tout le volume d'un coup (cadre multibloc de FactoryIO, maître +
parties), et le palier supérieur s'applique sur place en agrandissant le volume. Une serre bâtie
oblige à valider des motifs, à expliquer des erreurs de construction et à gérer des tailles
arbitraires ; des paliers de taille fixe gardent l'équilibre et la performance prévisibles.

## Conséquences

- Le palier fixe la taille : pas de serre sur mesure. Agrandir demande de la place libre autour.
- La taille n'étant pas libre, le gain de puissance passe par le palier, pas par la construction.
