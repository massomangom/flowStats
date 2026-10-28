# Courant Douala ⚡💧

Appli Android communautaire : les habitants de Douala signalent en un toucher les coupures
et retours de **courant** et d'**eau** dans leur quartier. Tout le quartier est alerté.

- **Revenus** : AdMob (bannière + interstitiel plafonné à 1 toutes les 5 min, jamais sur les 2 premiers signalements).
- **Autonomie** : aucun serveur à gérer (Firebase Firestore), aucune modération (pas de texte libre),
  données qui se « nettoient » seules (historique 48 h), notifications sans serveur (vérification locale toutes les 30 min).
- **Croissance** : bouton « Partager sur WhatsApp » avec l'état du quartier + lien Play Store ; demande d'avis Play au 5ᵉ signalement.

## Structure

| Dossier | Contenu |
|---|---|
| `app/src/main/java/.../logic/StatusLogic.kt` | Règles métier pures (confirmation, péremption, heures sans courant) — testées |
| `app/src/main/java/.../data/` | Liste des quartiers, Firestore, mode démo, préférences |
| `app/src/main/java/.../ui/` | Écrans Compose (Mon quartier, Douala en direct, Réglages) |
| `app/src/main/java/.../ads/Ads.kt` | AdMob + consentement UMP |
| `app/src/main/java/.../work/StatusWorker.kt` | Alertes en arrière-plan |
| `firebase/firestore.rules` | Règles de sécurité Firestore |
| `store/` | Fiche Play Store, icône 512, bannière 1024×500, captures |
| `docs/politique-confidentialite.md` | Politique de confidentialité (à héberger en ligne) |

Sans `app/google-services.json`, l'appli tourne en **mode démo** (données fictives locales) : utile pour essayer.

---

## Mise en ligne — ce que VOUS devez faire (≈ 3 à 4 h, une seule fois)

Ces étapes demandent votre identité, un paiement ou une signature : personne ne peut les faire à votre place.

### 1. Firebase (gratuit, 20 min)
1. https://console.firebase.google.com → **Ajouter un projet** « courant-douala » (Analytics : non).
2. **Build > Firestore Database** → Créer → mode production → région `europe-west1` (la plus proche de l'Afrique centrale parmi les choix habituels — à vérifier dans la liste).
3. Onglet **Règles** → coller le contenu de `firebase/firestore.rules` → Publier.
4. **Build > Authentication** → Commencer → **Anonyme** → Activer.
5. **Paramètres du projet > Ajouter une appli Android** → package `com.massomangom.courantdouala` → télécharger `google-services.json`.

### 2. AdMob (gratuit, 20 min)
1. https://admob.google.com → créer le compte (mêmes nom/adresse que pour les paiements).
2. **Applis > Ajouter** → Android → « pas encore publiée » → nom « Courant Douala ».
3. Créer 2 blocs d'annonces : **Bannière** et **Interstitiel**.
4. Noter : l'**ID d'application** (`ca-app-pub-…~…`) et les 2 **ID de blocs** (`ca-app-pub-…/…`).
5. **Paiements** : renseigner le compte bancaire (virement). Vérifier que le Cameroun et votre banque sont acceptés dans la liste AdMob au moment de l'inscription.
6. **Confidentialité et messages** : créer un message RGPD (requis pour les visiteurs européens/diaspora).

### 3. Clé de signature (5 min)
```bash
cd courant-douala && ./scripts/generate-keystore.sh upload.jks
```
⚠️ Sauvegarder `upload.jks` + mots de passe (Google Drive privé + clé USB). Ne jamais la committer.

### 4. Construire l'AAB
**Option A — automatique (recommandé)** : dans GitHub → Settings → Secrets and variables → Actions, ajouter :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | sortie du script ci-dessus |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`upload`), `KEY_PASSWORD` | vos mots de passe |
| `GOOGLE_SERVICES_JSON` | contenu complet de `google-services.json` |
| `ADMOB_APP_ID`, `ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID` | IDs AdMob |

Puis créer un tag : `git tag courant-v1.0.0 && git push origin courant-v1.0.0`.
L'AAB signé apparaît dans **Actions > Courant Douala > Artifacts**.

**Option B — local** : copier `google-services.json` dans `app/`, créer `keystore.properties` :
```
KEYSTORE_FILE=upload.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=upload
KEY_PASSWORD=...
ADMOB_APP_ID=ca-app-pub-...~...
ADMOB_BANNER_ID=ca-app-pub-.../...
ADMOB_INTERSTITIAL_ID=ca-app-pub-.../...
```
puis `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.

La compilation release **refuse** de produire un AAB avec les IDs de test ou sans Firebase (sinon : zéro revenu).

### 5. Google Play Console (25 $ une fois)
1. https://play.google.com/console → compte **personnel** → vérification d'identité (pièce d'identité).
2. **Créer une appli** → « Courant Douala : coupures & eau », Appli, Gratuite.
3. Remplir avec `store/fiche-play-store.md` (textes, catégorie, sécurité des données, classification).
4. Politique de confidentialité : publier `docs/politique-confidentialite.md` à une URL publique (Google Sites gratuit, ou GitHub Pages) et coller l'URL.
5. **Test fermé obligatoire** pour les nouveaux comptes personnels : à ce jour, Google exige un nombre minimum de testeurs (12 au moment où ces lignes sont écrites) actifs pendant 14 jours avant la production — vérifier la règle en vigueur dans la console. Recruter des voisins / groupes WhatsApp.
6. Publier en production.

### 6. Après publication
- AdMob : associer l'appli à sa fiche Play Store.
- Firebase : quand vous dépassez ~500 utilisateurs actifs/jour, passer au forfait **Blaze** (paiement à l'usage, le quota gratuit reste inclus) pour éviter le blocage des lectures. Estimation : ~4 lectures par ouverture + 48 par utilisateur/jour pour les alertes.

### Mises à jour automatiques (optionnel)
Créer un compte de service Google Cloud avec accès à la Play Console, mettre son JSON dans le secret
`PLAY_SERVICE_ACCOUNT_JSON` : chaque tag `courant-v*` publie alors l'AAB sur la piste interne.

---

## Développement

```bash
./gradlew testDebugUnitTest assembleDebug          # tests + APK debug (mode démo sans Firebase)
./gradlew testDebugUnitTest -Pscreenshots           # régénère store/screenshots/ depuis les vrais écrans
```

Ajouter un quartier : `data/Quartiers.kt` (ne jamais renommer un id existant).
