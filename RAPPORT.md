# Rapport de Projet — Simulating the Internet of Things through the Web of Things

**Master Informatique — Parcours ISA**  
**Module :** Web/Data Services Based Integration  
**Auteurs :** Étudiants du Master ISA  
**Projet :** Projet 3 — Smart Lab (Web of Things)  
**Date :** Octobre 2026  

---

## Introduction & Présentation Générale

Ce projet a pour objectif de concevoir et déployer un écosystème **Web of Things (WoT)** fonctionnel, basé sur les principes fondamentaux du Web appliqué aux objets connectés : **Access → Find → Share → Compose**.

L'ensemble de l'architecture est simulé à l'échelle applicative par **quatre microservices Spring Boot 3 autonomes** s'exécutant sur des ports HTTP distincts, complétés par un **Dashboard Web interactif** :
* **Passerelle (Gateway - port 8080) :** Répertoire central, mandataire d'authentification (*Auth Proxy*), moteur de règles d'automatisation, hub Server-Sent Events (SSE) et serveur statique du Dashboard.
* **Thermostat (`thing-thermostat` - port 8081) :** Régulation thermique avec moteur de simulation physique continue (+0.2°C/s en chauffe, -0.05°C/s à l'arrêt) et émission d'événement de consigne atteinte (`targetReached`).
* **Lampe (`thing-lamp` - port 8082) :** Éclairage connecté modélisant l'état binaire (`on`), le niveau de gradation (`brightness` de 0 à 100), une action sans paramètre `toggle` et une action paramétrée `setBrightness`.
* **Capteur de Mouvement (`thing-motion` - port 8083) :** Détection de présence avec propriété en lecture seule (`lastMotion`) et action de simulation (`simulateMotion`).

L'ensemble du système démarre de manière déterministe via une commande unique (`./run.sh`) et satisfait l'intégralité des conventions d'API et scénarios du cahier des charges (validé par `check-routes.sh` avec 38/38 tests validés).

```
                      ARCHITECTURE GLOBALE DU SMART LAB
                      
                      ┌─────────────────────────────────┐
                      │    Navigateur / Dashboard Web   │
                      │         (localhost:8080/)       │
                      └────────────────┬────────────────┘
                                       │ HTTP REST + Bearer Token
                                       │ Flux SSE: GET /events/stream?token=...
                                       ▼
                      ┌─────────────────────────────────┐
                      │      PASSERELLE (Gateway)       │
                      │           Port 8080             │
                      │  • Registre dynamique (/things) │
                      │  • Reverse Proxy sécurisé       │
                      │  • Webhook événements (/events) │
                      │  • Moteur de règles (R1, R2)    │
                      │  • TokenFilter & SSE Hub        │
                      └────┬───────────┬───────────┬────┘
                           │           │           │
              HTTP direct  │           │           │ HTTP direct
              (RestClient) │           │           │ (RestClient)
                           ▼           ▼           ▼
                     ┌───────────┐┌───────────┐┌───────────┐
                     │Thermostat ││   Lampe   ││Capteur Mvt│
                     │ Port 8081 ││ Port 8082 ││ Port 8083 │
                     └───────────┘└───────────┘└───────────┘
```

---

## 1. Access (Accès aux Ressources)

Le niveau **Access** repose sur l'exposition des objets physiques sous forme de ressources Web standardisées manipulables via des protocoles et formats légers (HTTP/REST et JSON).

### Modélisation des Ressources et Affordances
Chaque objet connecté décline ses interactions en trois affordances canoniques du Web of Things :
1. **Propriétés (`/properties` et `/properties/{name}`) :** Représentent l'état interne de l'objet.
   * La lecture retourne systématiquement l'encapsulation normalisée : `{"name": "...", "value": ...}`.
   * L'écriture via `PUT` attend un corps normalisé `{"value": ...}`.
2. **Actions (`/actions/{name}`) :** Représentent des processus déclenchables. L'appel `POST` accepte soit un corps vide (action idempotente ou sans paramètre comme `toggle` ou `simulateMotion`), soit un payload `{"value": ...}` (comme `setBrightness` ou `setTarget`).
3. **Événements :** Émis de façon asynchrone par les objets lorsqu'un changement d'état significatif survient (`propertyChanged`, `motion`, `targetReached`).

### Implémentation du pattern *Auth Proxy*
Pour garantir l'isolation des équipements et prévenir toute fuite de sécurité, le Dashboard et les clients externes n'interrogent **jamais** directement les microservices des objets. Toutes les interactions transitent par le mandataire inverse (*Reverse Proxy*) de la passerelle :
* `GET /things/{id}/properties` $\rightarrow$ relayé vers `GET {baseUrl}/properties`
* `GET|PUT /things/{id}/properties/{name}` $\rightarrow$ relayé vers `GET|PUT {baseUrl}/properties/{name}`
* `POST /things/{id}/actions/{name}` $\rightarrow$ relayé vers `POST {baseUrl}/actions/{name}`

Ce relais est implémenté via la classe `ThingClient` utilisant le `RestClient` moderne de Spring Boot 3 configuré avec des délais de garde stricts (connexion 3s, lecture 5s).

### Gestion Rigoureuse des Statuts HTTP et Conventions d'Erreur
L'architecture garantit une gestion granulaire des statuts HTTP, centralisée dans `ErrorHandler` (`@RestControllerAdvice`) :
* **`200 OK` :** Lecture, écriture de propriété ou exécution d'action réussie.
* **`201 Created` :** Enregistrement d'un objet avec en-tête `Location: /things/{id}`.
* **`202 Accepted` :** Réception et prise en compte d'un événement webhook (`POST /events`).
* **`204 No Content` :** Désenregistrement propre d'un objet (`DELETE /things/{id}`).
* **`400 Bad Request` :** Données invalides, valeur hors bornes (ex. luminosité à 150 ou mode `"turbo"`), champ manquant ou tentative d'écriture sur une propriété en lecture seule (`temperature` sur le thermostat ou `lastMotion` sur le capteur).
* **`401 Unauthorized` :** Jeton Bearer absent ou invalide.
* **`403 Forbidden` :** Tentative de modification (`PUT`, `POST`, `DELETE`) avec un jeton en lecture seule (*role viewer*).
* **`404 Not Found` :** Objet, propriété ou action inexistant dans le registre ou sur l'équipement.
* **`409 Conflict` :** Enregistrement d'un équipement dont l'identifiant est déjà présent.
* **`502 Bad Gateway` :** Équipement cible injoignable, arrêté ou en timeout réseau (interception des `ResourceAccessException`).

Toutes les réponses d'erreur respectent scrupuleusement la structure JSON : `{"status": <code_http>, "error": "<message explicite>"}`.

---

## 2. Find (Découverte & Répertoire des Objets)

Le niveau **Find** fournit les mécanismes nécessaires à la localisation dynamique et à l'auto-description des objets connectés sans configuration manuelle préalable de la passerelle.

### Auto-enregistrement Dynamique
Au démarrage de chaque microservice d'objet, la classe `GatewayClient` déclenche une requête HTTP d'auto-enregistrement :
```http
POST /things HTTP/1.1
Authorization: Bearer operator-secret
Content-Type: application/json

{
  "id": "lamp",
  "name": "Lamp",
  "baseUrl": "http://localhost:8082",
  "model": { ... }
}
```
* La passerelle vérifie la présence des champs obligatoires (`id`, `baseUrl`) et l'unicité de l'identifiant dans son registre thread-safe (`ConcurrentHashMap<String, Thing>`).
* Si l'objet est déjà enregistré, la passerelle renvoie `409 Conflict`.
* En cas de succès, la passerelle répond `201 Created`, valorise l'en-tête `Location: /things/{id}` et diffuse un événement SSE de type `registry` informant instantanément les dashboards connectés de l'apparition du nouvel équipement.
* Lors de l'extinction du microservice, un hook d'arrêt propre (`@PreDestroy`) émet `DELETE /things/{id}` (`204 No Content`) pour libérer l'entrée dans le répertoire.

### Modèle de Description (*Web Thing Model*)
Chaque objet s'auto-décrit sur sa route racine `GET /model` conformément au standard Web Thing Model vu en cours :
* Métadonnées fondamentales : nom, description textuelle, version.
* Déclaration des propriétés typées : type de donnée (`boolean`, `integer`, `number`, `string`), contraintes de bornes (`minimum`, `maximum`), mutabilité (`readOnly`).
* Déclaration des actions : paramètres attendus et schéma de payload.
* Liens hypermédias : pointeurs relatifs vers les collections de ressources (`"links": {"properties": "/properties", "toggle": "/actions/toggle", ...}`).

### Consultation du Catalogue
* `GET /things` : Liste l'ensemble des équipements enregistrés avec leurs liens d'accès individuels (`self: /things/{id}`).
* `GET /things/{id}` : Retourne la fiche technique intégrale et le modèle de l'objet sélectionné.

---

## 3. Share (Partage Sécurisé & Contrôle d'Accès)

Le niveau **Share** garantit que les objets et services ne peuvent être manipulés que par des entités dûment authentifiées et autorisées, tout en isolant le réseau interne.

### Authentification par Jeton Bearer
L'accès à l'API de la passerelle (`/things/**` et `/events/**`) est strictement conditionné par la fourniture d'un en-tête HTTP d'autorisation :
```http
Authorization: Bearer <token>
```
Ce filtrage est opéré par `TokenFilter` (`OncePerRequestFilter`), qui intercepte toute requête entrante. En cas d'omission ou d'invalidité du jeton, la requête est immédiatement rejetée avec le statut `401 Unauthorized` et l'en-tête de défi `WWW-Authenticate: Bearer`.

Les fichiers statiques du Dashboard (`/`, `index.html`, ressources CSS et JavaScript) sont quant à eux laissés publics pour permettre au navigateur d'afficher la mire de contrôle et d'inviter l'utilisateur à saisir son jeton.

### Gestion des Rôles : Opérateur vs Observateur (*Bonus Implémenté*)
Un système de contrôle d'accès basé sur les rôles (RBAC) a été intégré dans `TokenFilter` :
* **Rôle `operator` (jeton : `operator-secret`) :** Droits administrateurs complets (lecture, écriture de propriétés, déclenchement d'actions, enregistrement d'objets).
* **Rôle `viewer` (jeton : `viewer-secret`) :** Droits restreints en lecture seule. Les requêtes `GET` sont honorées (`200 OK`), tandis que toute tentative d'altération d'état (`PUT`, `POST`, `DELETE`) est bloquée avec le statut **`403 Forbidden`** (`{"status":403,"error":"forbidden: read-only access"}`).

### Adaptation Sécurisée pour les Server-Sent Events (SSE)
L'API native du navigateur `EventSource` ne permettant pas de définir des en-têtes HTTP personnalisés (`Authorization`), le filtre intègre une dérogation sécurisée strictement limitée à la route d'abonnement au flux :
```http
GET /events/stream?token=operator-secret
```
Le jeton transmis en paramètre d'URL est validé avec les mêmes exigences cryptographiques avant d'établir la connexion persistante en streaming.

### Contrat d'Interface OpenAPI 3.1
L'ensemble des contrats de la passerelle (endpoints, schémas de données, corps de requête, codes d'erreur et modèle de sécurité `bearerAuth`) est formalisé dans le fichier [gateway.yaml](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/starter-kit/gateway.yaml), également consultable et téléchargeable directement depuis le Dashboard.

---

## 4. Compose (Composition de Services & Automatisation)

Le niveau **Compose** donne toute sa valeur ajoutée au Web of Things en orchestrant plusieurs équipements hétérogènes au sein d'interactions automatisées transverses.

### Bus d'Événements Temps Réel (Webhooks & SSE)
1. **Émission d'événements par Webhook (`POST /events`) :** Lorsqu'un événement survient (détection de présence, consigne de température atteinte, mise à jour d'état), l'objet émetteur poste un payload structuré vers la passerelle. L'appel est déporté dans un thread asynchrone (`CompletableFuture.runAsync`) au sein de l'objet pour prévenir tout risque de contention ou d'interblocage (*deadlock*).
2. **Diffusion SSE (`GET /events/stream`) vs WebSockets :** La classe `EventHub` maintient la liste des flux `SseEmitter` actifs, assure le maintien de connexion périodique (*heartbeat ping* toutes les 15 secondes) et rediffuse chaque événement aux clients Web connectés en temps réel. Le choix des **Server-Sent Events** plutôt que des WebSockets répond directement à la nature du besoin : le canal est strictement unidirectionnel (push serveur vers client, les requêtes ascendantes transitant par les endpoints REST). SSE s'appuie sur le protocole HTTP standard (sans surcoût d'upgrade full-duplex de socket), bénéficie de la reconnexion automatique native dans le navigateur via l'API `EventSource`, et traverse sans friction les proxys et pare-feux.

### Moteur de Règles d'Automatisation (`RulesEngine`)
La passerelle intègre un moteur d'inférence événementiel évaluant les règles du scénario de référence :

```
                  ORCHESTRATION DES RÈGLES R1 & R2
                  
  [Capteur Mouvement]
          │
          │ POST /events (motion)
          ▼
   ┌──────────────┐      Temp < 19°C       ┌──────────────────┐
   │ Passerelle   ├───────────────────────►│  R2: Chauffage    │
   │ RulesEngine  │                        │  mode=heat       │
   └──────┬───────┘                        │  target=19.0°C   │
          │                                └─────────┬────────┘
          │ Motion                                   │ Temp >= 19°C
          ▼                                          ▼
   ┌──────────────┐                        ┌──────────────────┐
   │ R1: Éclairage│                        │ R2 Fin : Arrêt   │
   │ Lampe ON     │                        │ targetReached    │
   │ Timer T1     │                        │ mode=off         │
   └──────┬───────┘                        └──────────────────┘
          │ Expiration T1 (10s sans mouvement)
          ▼
   ┌──────────────┐
   │ Lampe OFF    │
   └──────────────┘
```

#### Règle R1 : Éclairage Intelligent Réarmable
* **Déclencheur :** Réception de l'événement `motion`.
* **Action :** Allumage immédiat de la lampe via `PUT http://localhost:8082/properties/on` (`{"value": true}`). Conformément aux exigences strictes du sujet, l'action `toggle` n'est jamais employée pour éviter d'éteindre une lampe déjà allumée.
* **Gestion du délai $T_1$ :** Un minuteur réarmable (`RestartableTimer` s'appuyant sur Spring `TaskScheduler`) est armé pour une durée paramétrable (10 secondes en démonstration). Toute nouvelle détection de mouvement annule la planification précédente et relance le compte à rebours.
* **Extinction :** À l'échéance du délai sans nouvelle détection, la passerelle éteint la lampe (`PUT /properties/on` à `false`).

#### Règle R2 : Confort Thermique Multi-Conditions
* **Déclencheur :** Détection de `motion` **ET** température ambiante actuelle strictement inférieure à 19.0°C (interrogation synchrone du thermostat).
* **Action :** Basculement du thermostat en mode `heat` avec une consigne fixée à `19.0°C` (`target = 19.0`).
* **Terminaison :** Lorsque le moteur de simulation du thermostat atteint 19.0°C, il émet l'événement `targetReached`. Si le thermostat est toujours en mode `heat`, la règle bascule immédiatement son mode à `off`. En mode `eco`, cet événement est ignoré.

#### Principes d'Isolation des Appels
Les commandes exécutées par les règles d'automatisation ciblent **directement** les ports des objets (8081, 8082) sans passer par le proxy de la passerelle. Ce choix architectural fondamental évite que les écritures automatiques ne soient interprétées comme des interventions manuelles d'un opérateur (prérequis indispensable pour la règle de débrayage R4).

### Simulation Thermique Temporelle Réaliste
Le thermostat embarque une tâche cadencée à 1 Hz (`@Scheduled(fixedRate = 1000)`) :
* **Modes `heat` et `eco` :** La température s'élève de $+0.2^\circ\text{C/s}$ jusqu'à la consigne. À la première traversée par le bas, l'événement `targetReached` est émis une seule fois (verrouillé par un drapeau atomique `targetReachedEmitted` jusqu'à la prochaine modification de consigne ou de mode), puis la température est maintenue stable.
* **Mode `off` :** La température redescend progressivement vers la température extérieure ($15.0^\circ\text{C}$) au rythme de $-0.05^\circ\text{C/s}$.
* **Température initiale :** Calée à $17.5^\circ\text{C}$ au démarrage pour permettre la démonstration instantanée de la règle R2.

---

## 5. Limits and Possible Improvements (Limites & Améliorations)

Bien que le démonstrateur implémente l'ensemble des spécifications fonctionnelles et techniques exigées, plusieurs limites d'ingénierie peuvent être identifiées et perfectionnées pour un déploiement en conditions réelles :

### Limites Identifiées
1. **Volatilité de l'État en Mémoire :**  
   L'annuaire des équipements enregistrés et les historiques d'événements sont stockés dans des collections en mémoire (`ConcurrentHashMap`). Un redémarrage inopiné de la passerelle entraîne la perte du registre jusqu'à ce que les objets redéclenchent leur procédure d'enregistrement ou que la passerelle les sollicite.
2. **Authentification par Clés Statiques Partagées :**  
   L'usage de jetons statiques pré-partagés (`operator-secret`, `viewer-secret`) convient parfaitement à un démonstrateur de laboratoire mais présente des limites en production (absence de révocation dynamique, pas de chiffrement asymétrique, pas d'horodatage d'expiration comme dans OAuth2/JWT).
3. **Couplage Réseau des Règles d'Automatisation :**  
   Le moteur de règles cible pour l'instant des URLs configurées statiquement (`http://localhost:8081`, `http://localhost:8082`). Dans une optique pleinement orientée services, la passerelle devrait résoudre dynamiquement l'équipement à solliciter en consultant les affordances sémantiques présentes dans son registre.
4. **Détection Tardive des Déconnexions :**  
   Si un objet cesse brutalement de fonctionner, la passerelle ne le constate qu'au moment où un client tente d'y accéder (génération d'un code 502 Bad Gateway), plutôt que de maintenir un statut de santé proactif.

### Pistes d'Amélioration & Perspectives d'Évolution
1. **Sonde de Disponibilité Périodique (*Liveness Check*) :**  
   Mettre en place une tâche planifiée dans la passerelle interrogeant périodiquement `GET /model` (toutes les 10 secondes) sur chaque objet enregistré afin de marquer dynamiquement l'état `online` ou `offline` au sein du catalogue `GET /things`.
2. **Implémentation des Règles R3 et R4 :**  
   * **R3 (Économie d'énergie) :** Programmation d'un timer $T_3$ déclenché par l'absence prolongée de mouvement pour basculer le thermostat en mode `eco` à 17°C.  
   * **R4 (Priorité à l'utilisateur / Débrayage manuel) :** Détection de toute écriture manuelle initiée via le proxy pour suspendre temporairement les règles automatiques sur l'objet concerné durant un délai $T_4$ ou jusqu'à reprise explicite (`POST /things/{id}/automation/resume`).
3. **Adoption Intégrale de la Recommandation W3C Thing Description (TD) :**  
   Faire évoluer le format descriptif du modèle vers du **JSON-LD conforme au W3C WoT Thing Description 1.1**, permettant l'intégration de contextes sémantiques (`@context: https://www.w3.org/2022/wot/td/v1.1`), d'ontologies SAREF et d'hyperliens HATEOAS formels.
4. **Sécurisation Mutuelle Inter-Services (*Thing Gateway Key*) :**  
   Restreindre l'écoute des objets pour qu'ils n'acceptent des requêtes entrantes que si elles proviennent de la passerelle (vérification d'une clé d'interconnexion partagée ou authentification mutuelle TLS).

---

## Conclusion & Synthèse de Validation

L'environnement **Smart Lab** développé dans ce projet constitue une implémentation robuste, fidèle et performante des concepts du Web of Things. Il concrétise l'ensemble de la chaîne de valeur :
* **Access :** API RESTful expressive, proxy d'authentification robuste, gestion d'erreurs 502 et statuts HTTP stricts.
* **Find :** Enregistrement dynamique au démarrage avec notification temps réel.
* **Share :** Sécurité granulaire par jetons Bearer différenciant les rôles `operator` et `viewer` (403 Forbidden).
* **Compose :** Orchestration réactive d'événements (SSE + Webhooks) et régulation physique multi-critères.

Le projet a été intégralement validé par la suite de tests automatisée `check-routes.sh` (score parfait de **38/38 tests passés**), dispose d'un contrat OpenAPI 3.1 exhaustif (`gateway.yaml`), et offre un tableau de bord web moderne pour le pilotage et la démonstration en temps réel.
