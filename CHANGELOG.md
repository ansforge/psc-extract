# Changelog — psc-extract

## [2026-11-04]

### Corrigé

#### Fixtures de test périmées
`multiple-pages-result.txt` et son `.sha256` dataient d'avant les commits `cab6b15` et
`3cfa012` (mars 2026), qui remplissent « Nom d'exercice » et « Prénom d'exercice » en se
rabattant sur le nom légal du PS quand l'exercice n'en porte pas. Les fixtures n'avaient pas
été régénérées, laissant trois tests de `ExtractionControllerTests` en échec depuis.
Seules les colonnes 17 et 18 changent, sur 6 lignes ; le comportement du code n'est pas touché.

## [2026-10-13]

### Ajouté

#### Extract « RASS partenaire » déposé par EDIT
Le RASS étant arrêté début 2027, EDIT produit chaque nuit un extract au format RASS partenaire
(cartes, identités de test et identités PSI) que `psc-extract` met à disposition à côté de son
propre extract.

- `POST /pscextract/v1/upload/edit` — dépôt du zip par EDIT (`multipart/form-data`, paramètre
  `file`). Authentifié par l'en-tête `X-Upload-Token`, comparé en temps constant à la propriété
  `edit.upload.token` : `401` si le jeton est absent ou invalide, `503` si la propriété n'est pas
  configurée (le dépôt est alors désactivé), `400` si le fichier n'est pas un zip, `202` en cas de
  succès. L'écriture passe par un fichier temporaire puis un déplacement atomique, pour qu'un
  téléchargement concurrent ne voie jamais un fichier partiel.
- `GET /pscextract/v1/download/edit` — téléchargement du dernier zip déposé, `404` tant qu'aucun
  dépôt n'a eu lieu.
- Le fichier est stocké dans le sous-répertoire `edit/` du volume des extracts : le `cleanup` des
  extracts RASS ne descend pas dans les sous-répertoires, il ne peut donc pas le supprimer.
- Nouvelles propriétés : `edit.upload.token` (valeur Vault `edit_upload_token`, ajoutée au
  `pscextract.nomad.tpl`) et `edit.extract.name` (défaut `Extraction_EDIT_partenaire.zip`).
- **Aucun impact** sur les routes existantes `/extract`, `/download` et `/cleanup`.

### Modifié

#### Limite de taille des dépôts
- `spring.servlet.multipart.max-file-size` et `max-request-size` passent de 10 à 50 Mo, dans
  `application.properties` **et** dans `pscextract.nomad.tpl` (qui surcharge ces deux propriétés en
  environnement déployé). À 10 Mo, le dépôt nocturne d'EDIT aurait été refusé par un `413` dès que
  l'extract aurait dépassé la limite, et le fichier publié serait resté figé sur la version de la
  veille. La route `/upload/test` bénéficie de la même limite.

## [2026-04-29]

### Modifié

#### `pom.xml`
- Bump dépendance `psc-api-client 2.3.8-SNAPSHOT → 2.3.9-SNAPSHOT`.
- **Aucun impact fonctionnel** sur l'export CSV : les nouveaux champs (`Ps.usualLastName`, `Practice.sourceId`) ne sont pas utilisés par l'extract, qui continue d'extraire `lastName` (nom légal). Le bump est uniquement pour l'alignement de version dans l'écosystème.
