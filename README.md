# Smart Lab — Web of Things (WoT) Demonstrator

Projet 3 — *Web/Data Services Based Integration*  
Master Informatique — Parcours ISA, Université de Tours

---

## 📋 Présentation Générale

Ce projet implémente un écosystème **Web of Things (WoT)** complet et fonctionnel simulant un laboratoire intelligent (*Smart Lab*). L'architecture est composée de **4 microservices autonomes Spring Boot 3** communicant par HTTP/REST, Webhooks et Server-Sent Events (SSE), complétés par un tableau de bord Web moderne :

1. **Passerelle (Gateway - port 8080) :** Répertoire central dynamique (`/things`), mandataire inverse sécurisé (*Auth Proxy*), moteur d'automatisation des règles R1 et R2, hub Server-Sent Events (`/events/stream`), contrôle d'accès RBAC (opérateur/observateur) et serveur statique du Dashboard.
2. **Thermostat (`thing-thermostat` - port 8081) :** Simulation physique continue (+0.2°C/s en chauffe, -0.05°C/s en refroidissement), gestion des modes `heat`, `eco`, `off`, émission de l'événement `targetReached`.
3. **Lampe (`thing-lamp` - port 8082) :** État binaire `on`, gradateur `brightness` (0-100), actions `toggle` et `setBrightness`.
4. **Capteur de Mouvement (`thing-motion` - port 8083) :** Propriété `lastMotion`, action `simulateMotion` et émission de l'événement `motion`.

Le contrat d'interface OpenAPI de la passerelle est disponible dans [`gateway.yaml`](gateway.yaml).

---

## ⚙️ Prérequis & Environnement

* **Système d'exploitation :** Linux, macOS ou Windows (avec WSL ou Git Bash).
* **Java Development Kit :** JDK 17 ou supérieur (recommandé : JDK 21).
* **Build Tool :** Apache Maven 3.8+ (ou 3.9+).
* **Outils CLI :** `bash` et `curl` pour l'exécution des scripts de démarrage et de validation.
* **Navigateur Web :** Chrome, Firefox, Safari ou Edge récent (support natif de `EventSource` pour SSE).

---

## 🚀 Installation & Démarrage

### 1. Cloner le dépôt
```bash
git clone <URL_DU_DEPOT>
cd projet3NLR4
```

### 2. Démarrer l'ensemble des services en une seule commande
Le script `run.sh` dans `starter-kit` compile l'ensemble des modules Maven si nécessaire et lance les 4 microservices en arrière-plan :

```bash
cd starter-kit
./run.sh
```

*Pour Windows PowerShell :*
```powershell
cd starter-kit
.\run.ps1
```

> **Astuces :**
> - Pour relancer sans recompiler Maven : `SKIP_BUILD=1 ./run.sh`
> - Pour modifier le délai d'extinction $T_1$ de la lampe : `T1=10 ./run.sh` (défaut : 10s pour démo, 60s en production)
> - Pour arrêter tous les services proprement : appuyez sur `Ctrl + C`.

### 3. Accéder au Dashboard Web
Ouvrez votre navigateur sur : **[http://localhost:8080/](http://localhost:8080/)**  
Le jeton d'accès par défaut est `operator-secret`.

---

## 🧪 Validation & Tests Automatisés

Le script de test `check-routes.sh` valide l'intégralité des routes obligatoires, les statuts HTTP et les conventions d'API (codes 200, 201, 202, 204, 400, 401, 403, 404, 409, 502) :

```bash
cd starter-kit
VIEWER=viewer-secret ./check-routes.sh
```

**Résultat attendu :** `38 passed, 0 failed` (100% de réussite incluant les tests de rôles RBAC).

---

## 🎬 Scénario de Démonstration (Reproduction pas à pas)

### Étape 1 : Découverte & Consultation du Registre
```bash
T="Authorization: Bearer operator-secret"

# Liste des objets enregistrés
curl -H "$T" http://localhost:8080/things

# Description sémantique d'un objet (Web Thing Model)
curl http://localhost:8082/model
```

### Étape 2 : Manipulation des Propriétés & Actions via le Proxy
```bash
# Allumer la lampe via le proxy de la passerelle
curl -X PUT -H "$T" -H "Content-Type: application/json" -d '{"value":true}' http://localhost:8080/things/lamp/properties/on

# Ajuster la luminosité à 75%
curl -X PUT -H "$T" -H "Content-Type: application/json" -d '{"value":75}' http://localhost:8080/things/lamp/properties/brightness

# Inverser l'état (Action toggle)
curl -X POST -H "$T" http://localhost:8080/things/lamp/actions/toggle
```
*Vérifiez sur le Dashboard que les jauges et états se mettent à jour instantanément sans recharger la page (via SSE).*

### Étape 3 : Règle R1 — Éclairage Automatique Réarmable
1. Éteignez la lampe : `curl -X PUT -H "$T" -H "Content-Type: application/json" -d '{"value":false}' http://localhost:8080/things/lamp/properties/on`
2. Simulez une détection de présence :
   ```bash
   curl -X POST -H "$T" http://localhost:8080/things/motion/actions/simulateMotion
   ```
3. **Constat :** La lampe s'allume immédiatement (`PUT /properties/on` à `true`). Le minuteur $T_1$ (10s) s'enclenche.
4. Si un nouveau mouvement est simulé avant 10s, le compte à rebours est réarmé.
5. Après 10s sans mouvement, la lampe s'éteint automatiquement.

### Étape 4 : Règle R2 — Confort Thermique Multi-Conditions
1. Vérifiez que la température du thermostat est inférieure à 19°C (au démarrage : 17.5°C, mode `off`).
2. Déclenchez un mouvement :
   ```bash
   curl -X POST -H "$T" http://localhost:8080/things/motion/actions/simulateMotion
   ```
3. **Constat :** La règle R2 bascule le thermostat en mode `heat` avec consigne `target = 19.0°C`.
4. Observez la montée progressive de la température à l'écran (+0.2°C/s).
5. Lorsque 19.0°C est atteint, le thermostat émet l'événement `targetReached` par Webhook.
6. La passerelle intercepte l'événement et bascule immédiatement le thermostat en mode `off`.

### Étape 5 : Sécurité & Contrôle d'Accès (RBAC)
```bash
# Requête refusée sans token -> 401 Unauthorized
curl http://localhost:8080/things

# Requête avec token observateur en lecture -> 200 OK
curl -H "Authorization: Bearer viewer-secret" http://localhost:8080/things

# Requête avec token observateur en écriture -> 403 Forbidden (Bonus Rôles)
curl -X PUT -H "Authorization: Bearer viewer-secret" -H "Content-Type: application/json" -d '{"value":true}' http://localhost:8080/things/lamp/properties/on
```

---

## 📂 Structure du Répertoire

```
.
├── RAPPORT.md               # Rapport technique complet du projet (sections WoT)
├── RAPPORT.pdf              # Version compilée PDF (2-3 pages requises)
├── README.md                # Guide d'installation et d'exécution principal (ce fichier)
├── gateway.yaml             # Spécification OpenAPI 3.1 de la passerelle
├── project3.pdf             # Sujet officiel du projet
├── TUTORIAL.md              # Tutoriel pas-à-pas interactif
└── starter-kit/             # Code source Maven multi-modules
    ├── pom.xml              # POM parent
    ├── run.sh               # Script de lancement global
    ├── check-routes.sh      # Suite de tests d'intégration (38 tests)
    ├── gateway/             # Microservice Passerelle (port 8080)
    ├── thing-thermostat/    # Microservice Thermostat (port 8081)
    ├── thing-lamp/          # Microservice Lampe (port 8082)
    └── thing-motion/        # Microservice Détecteur de Mouvement (port 8083)
```

## 🏷️ Remise du Projet

Conformément aux consignes du sujet :
- Dépôt Git avec le tag : `v1.0` (`git tag -a v1.0 -m "Release v1.0" && git push origin v1.0`)
- Rapport au format PDF (`RAPPORT.pdf`)
- Démonstration vidéo MP4 (environ 3 minutes)
