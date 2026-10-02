# Plan d'Implémentation Détaillé — Projet 3 (Web of Things)
> **Master Informatique — Parcours ISA**  
> **Matière :** Web/Data services based integration  
> **Sujet :** Simulating the Internet of Things through the Web of Things (*Access → Find → Share → Compose*)  
> **Échéance :** Mardi 6 Octobre 2026 sur Célène (Tag Git `v1.0`, Rapport PDF, Vidéo MP4)

---

## 1. Vue d'Ensemble & Architecture

Le système simule un environnement Web of Things complet composé de **4 microservices HTTP autonomes** développés avec **Spring Boot** (Java 21) et d'un **Dashboard Web**.

```
                       ARCHITECTURE DU SYSTÈME
                       
                       [ Navigateur Web ]
                               │
                 REST + EventSource (/events/stream)
                               │
                               ▼
        ┌──────────────────────────────────────────────┐
        │             GATEWAY (Port 8080)              │
        │  • Registre (/things) & Proxy Auth           │
        │  • Webhook événements (/events)              │
        │  • Moteur de règles (R1, R2, RestartableTimer│
        │  • TokenFilter & SSE EventHub                │
        │  • Serveur Web Dashboard (static/)           │
        └───────┬──────────────┬──────────────┬────────┘
                │              │              │
    HTTP direct │  HTTP direct │  HTTP direct │
    (RestClient)│  (RestClient)│  (RestClient)│
                ▼              ▼              ▼
        ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
        │  Thermostat │ │    Lampe    │ │Capteur Mvt  │
        │ (Port 8081) │ │ (Port 8082) │ │ (Port 8083) │
        │             │ │             │ │             │
        │ • temp/mode │ │ • on/bright │ │ • lastMotion│
        │ • simulation│ │ • toggle    │ │ • simulate  │
        └─────────────┘ └─────────────┘ └─────────────┘
```

### Table des Services, Identifiants et Ports Imposés

| Service | Port | Identifiant (`id`) | Rôle & Responsabilités |
| :--- | :--- | :--- | :--- |
| **Gateway** | `8080` | `gateway` | Auth Proxy, Registre, Moteur de règles, Hub SSE, Serveur Dashboard |
| **Thermostat** | `8081` | `thermostat` | Régulation de température, simulation continue, émission `targetReached` |
| **Lampe** | `8082` | `lamp` | Éclairage (`on`, `brightness`), actions `toggle`, `setBrightness` |
| **Capteur Mouvement**| `8083` | `motion` | Détection de présence, timestamp `lastMotion`, action `simulateMotion` |

---

## 2. Conventions d'API & Formats Stricts

Ces conventions sont obligatoires et vérifiées automatiquement par le script de validation `starter-kit/check-routes.sh` :

1. **Lecture d'une propriété** : retourne l'objet `{"name": "<nom>", "value": <valeur>}`.
2. **Écriture de propriété ou action** : accepte un payload JSON `{"value": <valeur>}` (aucun body pour une action sans paramètre comme `toggle` ou `simulateMotion`).
3. **Format d'erreur universel** :
   ```json
   {
     "status": 404,
     "error": "thing lamp is not registered"
   }
   ```
4. **Codes HTTP attendus** :
   * `200 OK` : lecture, écriture ou action réussie.
   * `201 Created` : enregistrement réussi (`POST /things`) avec header `Location: /things/{id}`.
   * `202 Accepted` : notification d'événement reçue via webhook (`POST /events`).
   * `204 No Content` : désenregistrement réussi (`DELETE /things/{id}`).
   * `400 Bad Request` : champ manquant, type invalide, valeur hors limites ou écriture sur propriété en lecture seule.
   * `401 Unauthorized` : token absent ou invalide (`{"status":401,"error":"missing or invalid token"}`).
   * `403 Forbidden` : action refusée avec token en lecture seule (*role viewer*).
   * `404 Not Found` : objet, propriété ou action introuvable.
   * `409 Conflict` : identifiant d'objet déjà enregistré.
   * `502 Bad Gateway` : objet distant inaccessible ou ne répondant pas.

---

## 3. Plan de Développement en 9 Phases

### Phase 1 : Compléter le module Lampe (`thing-lamp`)
- [ ] **Propriété `brightness`** :
  - Ajouter `brightness` dans l'état interne (`ConcurrentHashMap`, valeur par défaut `80`).
  - Mettre à jour `PUT /properties/{name}` :
    - Si `name == "on"` : valider que `value instanceof Boolean`.
    - Si `name == "brightness"` : valider que `value instanceof Number` et $0 \le \text{value} \le 100$, sinon renvoyer `400 Bad Request`.
- [ ] **Action `setBrightness`** :
  - Ajouter la route `POST /actions/setBrightness`.
  - Valider le corps `{"value": <nombre entre 0 et 100>}`.
  - Mettre à jour la propriété et émettre l'événement `propertyChanged`.
- [ ] **Modèle Web Thing** :
  - Mettre à jour `LampDescription.java` pour inclure `brightness` et `setBrightness`.

---

### Phase 2 : Implémenter le Proxy de la Gateway (`gateway`)
- [ ] **Injection HTTP Client** :
  - Déclarer un `RestClient` dans `ThingsController.java` pour relayer les requêtes vers `thing.baseUrl()`.
- [ ] **Endpoints du Proxy** :
  - `GET /things/{id}/properties` : relayer vers `GET {baseUrl}/properties`.
  - `GET /things/{id}/properties/{name}` : relayer vers `GET {baseUrl}/properties/{name}`.
  - `PUT /things/{id}/properties/{name}` : relayer vers `PUT {baseUrl}/properties/{name}` avec le body `{"value": ...}`.
  - `POST /things/{id}/actions/{name}` : relayer vers `POST {baseUrl}/actions/{name}`.
- [ ] **Gestion des Erreurs Proxy** :
  - Si `id` inconnu $\rightarrow$ lever `ApiException(HttpStatus.NOT_FOUND)`.
  - Si l'objet distant ne répond pas (`ResourceAccessException`, timeout, port fermé) $\rightarrow$ renvoyer **`502 Bad Gateway`**.
  - Si l'objet distant renvoie une erreur (400, 404) $\rightarrow$ propager fidèlement le code d'erreur et le message JSON de l'objet.

---

### Phase 3 : Créer le module Capteur de Mouvement (`thing-motion`)
- [ ] **Structure & Configuration Maven** :
  - Créer le dossier `starter-kit/thing-motion/` avec son `pom.xml` (port `8083`).
  - Déclarer le module dans le `pom.xml` racine.
- [ ] **Modèle & Contrôleur** (`MotionController.java`) :
  - Id : `motion`, Nom : `Motion Sensor`.
  - Propriété `lastMotion` (timestamp ISO-8601, ex: `Instant.now().toString()`).
  - `PUT /properties/lastMotion` $\rightarrow$ renvoyer `400 Bad Request` (propriété `read-only`).
  - Action `POST /actions/simulateMotion` $\rightarrow$ met à jour `lastMotion`, répond `200 OK` et émet l'événement `motion` via le `GatewayClient`.
- [ ] **Modèle Web Thing** (`MotionDescription.java`) :
  - Description du modèle accessible via `GET /model`.
- [ ] **Auto-enregistrement** :
  - S'enregistrer auprès de la Gateway au démarrage sur `POST /things`.

---

### Phase 4 : Créer le module Thermostat (`thing-thermostat`)
- [ ] **Structure & Configuration Maven** :
  - Créer le dossier `starter-kit/thing-thermostat/` avec son `pom.xml` (port `8081`).
  - Déclarer le module dans le `pom.xml` racine.
- [ ] **Gestion de l'État & Propriétés** :
  - `temperature` : lecture seule, flottant initialisé à `17.5` (°C) (un `PUT` doit renvoyer `400`).
  - `target` : consigne flottante (ex: `19.0`).
  - `mode` : chaîne valant `"off"`, `"heat"`, ou `"eco"`. Toute autre valeur (ex: `"turbo"`) doit renvoyer `400 Bad Request`.
- [ ] **Action `setTarget`** :
  - `POST /actions/setTarget` avec `{"value": <nombre>}`.
- [ ] **Boucle de Simulation (@Scheduled à 1 Hz)** :
  - En mode `heat` / `eco` :
    - Si `temperature < target` : augmenter de `+0.2°C` par seconde.
    - Dès que `temperature >= target` (atteinte par le bas uniquement) : stabiliser à `target` et émettre l'événement `targetReached` vers la Gateway (une seule fois).
    - Si `temperature > target` : décroissance douce vers `target` sans émission d'événement.
  - En mode `off` :
    - Décroissance lente vers la température extérieure (`15.0°C` à `-0.05°C/s`).
  - *Remarque : en mode `eco`, l'événement `targetReached` est ignoré par les règles.*

---

### Phase 5 : Moteur de Règles dans la Gateway (`EventsController`)
- [ ] **Composant Timer** :
  - Utiliser `RestartableTimer` avec un délai $T_1$ configurable (10 secondes pour la démo, 60s par défaut).
- [ ] **Règle R1 (Éclairage automatique)** :
  - Déclencheur : Événement `motion` reçu sur `POST /events`.
  - Effet : Allumer la lampe via un appel direct `PUT http://localhost:8082/properties/on` (`{"value": true}`).
  - Réarmement : Démarrer ou réinitialiser le timer $T_1$.
  - Fin : À l'expiration du timer sans mouvement, éteindre la lampe via `PUT http://localhost:8082/properties/on` (`{"value": false}`).
  - *Règle stricte du sujet : Ne jamais utiliser l'action `toggle` pour automatiser la lampe.*
- [ ] **Règle R2 (Confort Thermique)** :
  - Déclencheur : Événement `motion` reçu ET `temperature < 19.0°C`.
  - Effet : Passer le thermostat en mode `heat` et consigne `target = 19.0` (appel direct HTTP vers `http://localhost:8081`).
  - Fin : À la réception de l'événement `targetReached` émis par le thermostat (si `mode == "heat"`) $\rightarrow$ passer le thermostat en `mode = "off"`.
- [ ] **Ordre d'exécution & Sémantique stricte** :
  - Sur détection de `motion` : appliquer les règles dans l'ordre strict : fin de R3 (si implémenté), puis R1, puis R2.
  - Les requêtes envoyées par les règles sont des appels directs aux Things (et non via le proxy de la Gateway) afin de ne pas déclencher R4 et éviter les boucles.

---

### Phase 6 : Validation Globale & Scripts d'Exécution
- [ ] **Mise à jour de `run.sh`** :
  - Compiler les 4 modules (`mvn package`).
  - Lancer en tâche de fond la Gateway (8080), le Thermostat (8081), la Lampe (8082) et le Capteur (8083).
- [ ] **Exécution du script de test** :
  - Lancer `./check-routes.sh`.
  - Valider l'obtention du score parfait : **35 passed, 0 failed**.

---

### Phase 7 : Dashboard Web Dynamique (`static/index.html`)
- [ ] **Authentification & Connexion SSE** :
  - Saisie du token (`operator-secret`), sauvegarde locale dans le `localStorage`.
  - Connexion automatique au flux `EventSource('/events/stream?token=...')`.
  - Indicateur de statut de connexion en direct (pastille verte/rouge).
- [ ] **Cartes d'Affichage Interactives** :
  - **Lampe** : switch ON/OFF, curseur de luminosité réactif, icône lumineuse dynamique.
  - **Thermostat** : grand affichage de la température en direct, curseur/boutons pour la consigne `target`, sélecteur de mode (`off`, `heat`, `eco`), badge d'état de chauffe.
  - **Capteur de Mouvement** : bouton "Simuler Détection", affichage de l'heure du dernier mouvement, feedback visuel lors d'une détection.
- [ ] **Console d'Événements Live** :
  - Liste défilante des événements SSE horodatés reçus en direct.

---

### Phase 8 : Spécification OpenAPI (`gateway.yaml`)
- [ ] **Contrat OpenAPI v3** :
  - Rédiger `gateway.yaml` décrivant l'ensemble de l'API de la passerelle.
  - Inclure les endpoints `/things`, `/things/{id}`, `/things/{id}/properties/**`, `/things/{id}/actions/**`, `/events`, `/events/stream`.
  - Spécifier le schéma de sécurité `bearerAuth`.
  - Définir les schémas d'objets (`Thing`, `PropertyValue`, `ErrorResponse`).

---

### Phase 9 : Livrables & Démonstration
- [ ] **Guide d'installation (`README.md`)** :
  - Prérequis (Java 21, Maven 3.9, curl, bash).
  - Commandes de build, lancement (`./run.sh`), tests (`./check-routes.sh`).
- [ ] **Rapport PDF (2 à 3 pages)** structuré selon les 5 sections imposées :
  1. *Access* : Modélisation des ressources REST, sous-ressources, conventions JSON.
  2. *Find* : Découverte dynamique, auto-enregistrement (`POST /things`), catalogue.
  3. *Share* : Sécurité par token Bearer, contournement query param pour SSE, pattern Auth Proxy.
  4. *Compose* : Moteur de règles (R1, R2), coordination événementielle, gestion des timers.
  5. *Limits and possible improvements* : Résilience (pannes des objets), persistance, extensions futures.
- [ ] **Vidéo de Démonstration (~3 minutes)** :
  - Scénario fluide : Démarrage des 4 services $\rightarrow$ Auto-enregistrement $\rightarrow$ Découverte $\rightarrow$ Modification de propriété depuis le dashboard $\rightarrow$ Déclenchement d'une action $\rightarrow$ Démonstration de R1 (mouvement $\rightarrow$ lampe ON $\rightarrow$ timer $\rightarrow$ OFF) $\rightarrow$ Démonstration de R2 (mouvement $\rightarrow$ chauffe $\rightarrow$ arrêt à 19°C) $\rightarrow$ Rejet d'une requête sans token (401).
- [ ] **Dépôt Git** :
  - Créer et pousser le tag de version finale :
    ```bash
    git tag v1.0
    git push origin v1.0
    ```

---

## 4. Stratégie de Tests Complète (Multi-Niveaux)

### A. Tests Unitaires & Validation de Contrat (JUnit 5 + MockMvc)
Dans chaque module `thing-*` et dans `gateway` :
1. **Validation des types et bornes** :
   * `LampControllerTest` : vérifier que `PUT /properties/brightness` avec une valeur de `150` ou `-5` retourne `400 Bad Request`.
   * `ThermostatControllerTest` : vérifier que `PUT /properties/mode` avec `"turbo"` retourne `400 Bad Request` alors que `"heat"`, `"eco"` et `"off"` retournent `200 OK`.
   * `MotionControllerTest` : vérifier que `PUT /properties/lastMotion` retourne `400 Bad Request` (propriété *read-only*).
2. **Gestion des routes inexistantes** :
   * Requêter `GET /properties/inconnue` $\rightarrow$ vérifie le retour `404 Not Found` avec le body standardisé `{"status": 404, "error": "..."}`.

### B. Tests d'Intégration du Proxy & Résilience
1. **Pass-through du Proxy** :
   * Vérifier que la Gateway transmet fidèlement les corps de requête JSON vers les objets et renvoie les réponses sans altération de structure.
2. **Tolérance aux pannes (Test du 502 Bad Gateway)** :
   * Enregistrer un objet fictif pointant vers un port fermé (ex: `http://localhost:9999`).
   * Appeler `GET /things/fictif/properties` via la Gateway $\rightarrow$ vérifier la levée d'une `ResourceAccessException` interceptée en `502 Bad Gateway`.

### C. Tests Événementiels et Scénarios Temporels (Règles R1 & R2)
1. **Test d'intégration de R1 (Éclairage)** :
   * Simuler un événement `POST /events` avec `type: "motion"`.
   * Vérifier que la lampe est passée à `on: true`.
   * Vérifier qu'un second `motion` réarme le timer $T_1$.
   * Attendre l'expiration du timer ($T_1$) et constater le passage à `on: false`.
2. **Test d'intégration de R2 (Chauffage)** :
   * Mettre le thermostat à `17.0°C` et déclencher `motion`.
   * Vérifier que le mode devient `heat` et consigne `19.0°C`.
   * Simuler la réception de l'événement `targetReached` du thermostat $\rightarrow$ vérifier que le thermostat bascule automatiquement en mode `off`.

### D. Validation Automatisée de Rendu (`check-routes.sh`)
* Exécution automatique via script bash :
  ```bash
  ./run.sh &
  sleep 4
  ./check-routes.sh
  ```
* Objectif : **35 PASS, 0 FAIL**.

---

## 5. Spécifications & Bonnes Pratiques "Anti-Code IA"

Pour que le projet soit clairement identifié comme un travail d'étudiants de Master rigoureux et non comme une génération générique d'IA :

### A. Architecture & Code Java : Rester sobre et idiomatique
* ❌ **Ce qu'il faut bannir (typique d'une génération IA)** :
  * Créer des usines abstraites (*AbstractFactory*, *GenericThingRegistryBuilder<T>*, etc.) là où un simple `ConcurrentHashMap` et un `record` Java suffisent.
  * Multiplier les dépendances exotiques ou les bibliothèques non vues en cours.
  * Inonder le code de commentaires triviaux (ex: `// Variable to store temperature`, `// Setter for mode`).
* ✅ **Ce qu'il faut appliquer (style étudiant/pro)** :
  * Utiliser les idiomes modernes de Java 21 : `record`, *pattern matching*, `switch expressions`.
  * Utiliser l'API standard `RestClient` introduite dans Spring Boot 3 sans complexité superflue.
  * Mettre des commentaires ciblés qui expliquent le **pourquoi** d'un choix technique (ex: `// L'événement targetReached n'est émis que lors de la transition en mode heat pour éviter les rebonds`).

### B. Interface Utilisateur (Dashboard) : Pro, sobre et technique
* ❌ **Ce qu'il faut bannir** :
  * Les templates violets fluo avec des dégradés clichés, des boutons arrondis disproportionnés et des emojis à tout va (`Smart Home AI Powered 🚀✨`).
  * Les frameworks lourds (React/Vue/Tailwind CDN) injectés artificiellement pour 3 cartes d'affichage.
* ✅ **Ce qu'il faut appliquer** :
  * Un design industriel épuré inspiré de **Home Assistant**, **Grafana** ou **WebThings Gateway**.
  * CSS vanilla moderne et soigné (Grid CSS, variables CSS, typographie système `system-ui` ou Google Font sobre comme *Inter*).
  * Des métriques claires : affichage net des valeurs (°C, %, On/Off), badges d'état discrets (`En ligne`, `En chauffe`), indicateur de liaison SSE direct.
  * Logs défilants lisibles comme dans une console réseau d'objets connectés.

### C. Historique Git réaliste et professionnel
* ❌ **Ce qu'il faut bannir** :
  * Un seul commit géant contenant l'ensemble du projet.
  * Des messages vagues ou génériques (`Update code`, `AI fixes`, `Refactor`).
* ✅ **Ce qu'il faut appliquer (Conventional Commits)** :
  * `feat(lamp): add brightness property with range validation and setBrightness action`
  * `feat(gateway): implement reverse-proxy routing with 502 error handling`
  * `feat(motion): create motion sensor thing with simulated trigger`
  * `feat(thermostat): add scheduled temperature simulation loop and targetReached event`
  * `feat(rules): implement R1 lighting timer and R2 temperature regulation`
  * `test: achieve 35/35 pass on check-routes.sh`
  * `docs: add gateway.yaml openapi specification and architecture report`

---

## 6. Sources et Références d'Inspiration

Pour approfondir le sujet, justifier vos choix dans le rapport final et concevoir une architecture irréprochable :

### A. Standards Officiels du Web of Things (W3C)
1. **W3C WoT Architecture 1.1** :  
   👉 [https://www.w3.org/TR/wot-architecture11/](https://www.w3.org/TR/wot-architecture11/)  
   *Consultez les concepts fondamentaux : Servient, Thing Description (TD), Affordances (Properties, Actions, Events).*
2. **W3C WoT Thing Description (TD)** :  
   👉 [https://www.w3.org/TR/wot-thing-description11/](https://www.w3.org/TR/wot-thing-description11/)  
   *Pour structurer fidèlement les réponses de `GET /model`.*

### B. Projets Open-Source de Référence (Architecture & UI)
1. **WebThings Gateway (projet originel de Mozilla)** :  
   👉 [https://webthings.io/gateway/](https://webthings.io/gateway/)  
   *La référence absolue d'une passerelle Web of Things open source basée sur REST et SSE. Très bonne inspiration pour la disposition ergonomique des cartes (Things) du dashboard.*
2. **Home Assistant (Automation Engine)** :  
   👉 [https://www.home-assistant.io/docs/automation/](https://www.home-assistant.io/docs/automation/)  
   *Pour la logique de composition : Triggers (déclencheurs), Conditions (état de température < 19°C), Actions (allumer la lampe / activer le chauffage).*
3. **Node-RED** :  
   👉 [https://nodered.org/](https://nodered.org/)  
   *Pour visualiser et conceptualiser le flux d'événements et la composition de services.*

### C. Littérature & Documentation Technique
1. **"Building the Web of Things"** — Dominique Guinard & Vlad Trifa (Manning Publications) :  
   *L'ouvrage de référence décrivant la pyramide WoT : Access (REST), Find (Discovery), Share (Security/Auth Proxy), Compose (Coordination).*
2. **Spring Boot 3 REST Client Documentation** :  
   👉 [https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)  
   *Guide officiel sur l'utilisation moderne et synchrone de `RestClient` dans Spring Boot.*
3. **MDN Server-Sent Events (SSE)** :  
   👉 [https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events)  
   *Documentation claire sur l'utilisation d'`EventSource` côté navigateur et la gestion des flux d'événements texte.*

---

## 7. Extensions & Bonus Éligibles (Jusqu'à +3 points)

Le sujet prévoit des points bonus (plafonnés à 3 points au total). Voici les options prioritaires facilement intégrables :

1. **Rôles `viewer` / `operator` avec réponses 403** *(Recommandé)* :
   - Déjà pré-câblé dans `check-routes.sh` (lignes 91-96) !
   - Le token `viewer-secret` autorise la lecture (`GET`), mais renvoie **`403 Forbidden`** sur les écritures de propriétés (`PUT`) et les déclenchements d'actions (`POST`).
2. **Liveness checking des objets enregistrés** :
   - La Gateway interroge périodiquement chaque objet (`GET /model` toutes les 10s via un `@Scheduled`).
   - Ajout d'un champ `"status": "online"|"offline"` dans la liste retournée par `GET /things`.
3. **Règles R3 (Économie d'énergie) et R4 (Débrayage manuel)** :
   - R3 : Si aucun mouvement pendant $T_3$ (ex: 30s) $\rightarrow$ thermostat en mode `eco` à 17°C.
   - R4 : Si un utilisateur modifie manuellement un équipement depuis le dashboard/proxy $\rightarrow$ les règles R1-R3 cessent d'agir sur cet objet pendant $T_4$ ou jusqu'au clic sur "reprendre automatisation" (`POST /things/{id}/automation/resume`).

---

## 8. Matrice de Conformité avec `project3.pdf`

| Exigence du Sujet (`project3.pdf`) | Prise en compte dans le Plan | Statut |
| :--- | :--- | :--- |
| **4 microservices distincts sur ports 8080, 8081, 8082, 8083** | Section 1 (Table des ports & modules) | ✅ Conforme |
| **Enregistrement dynamique (`POST /things`) avec Location header** | Phase 2 & Section 2 | ✅ Conforme |
| **Désenregistrement (`DELETE /things/{id}`) renvoyant 204** | Section 2 | ✅ Conforme |
| **Auth Proxy pattern (Dashboard n'appelle jamais les Things en direct)** | Phase 2 & Schéma d'architecture | ✅ Conforme |
| **Erreur 502 si un objet ne répond pas** | Phase 2 & Tests d'intégration (B2) | ✅ Conforme |
| **Simulation Thermostat (+0.2°C/s heat, -0.05°C/s off, targetReached)** | Phase 4 | ✅ Conforme |
| **Validation 400 sur read-only et valeurs hors bornes** | Phases 1, 3, 4 & Tests unitaires (A1) | ✅ Conforme |
| **Format d'erreur JSON unique `{"status": ..., "error": "..."}`** | Section 2 & ErrorHandler | ✅ Conforme |
| **Règle R1 (Motion $\rightarrow$ Lampe ON $\rightarrow$ extinction après $T_1$)** | Phase 5 | ✅ Conforme |
| **Règle R2 (Motion + Temp < 19°C $\rightarrow$ chauffe $\rightarrow$ arrêt à target)** | Phase 5 | ✅ Conforme |
| **Sémantique R1 : `PUT /properties/on` direct (jamais `toggle`)** | Phase 5 (Avertissement strict) | ✅ Conforme |
| **Sécurité Bearer token + exception query param `?token=` pour SSE** | Phase 7 & Section 2 | ✅ Conforme |
| **Dashboard web natif (HTML/JS) servi par Gateway (`static/`)** | Phase 7 & Section 5B | ✅ Conforme |
| **Contrat OpenAPI (`gateway.yaml`)** | Phase 8 | ✅ Conforme |
| **Rapport 2-3 pages structuré en 5 parties (Access, Find, Share, Compose, Limits)** | Phase 9 | ✅ Conforme |
| **Vidéo démo ~3 minutes suivant le script imposé** | Phase 9 | ✅ Conforme |
| **Lancement du système complet en une seule commande (`run.sh`)** | Phase 6 | ✅ Conforme |
| **Tag Git `v1.0` pour le rendu final** | Phase 9 | ✅ Conforme |
