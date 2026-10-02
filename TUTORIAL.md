# 📘 Tutoriel Complet : Prise en main de **Smart Lab (Web of Things)**

Bienvenue sur le guide d'utilisation du projet **Smart Lab**. Ce système reproduit une passerelle domotique intelligente conforme au standard **W3C Web of Things (WoT)**, interconnectant 4 microservices en temps réel.

---

## 🏗️ 1. Vue d'Ensemble de l'Architecture

Le système est découpé en 4 microservices Spring Boot 3 autonomes :

| Service | Port | Rôle principal |
| :--- | :---: | :--- |
| **Passerelle (Gateway)** | `8080` | Registre dynamique, Auth Proxy, Moteur de règles (R1/R2), Bus SSE, Dashboard Web |
| **Thermostat** | `8081` | Simulation thermique en temps réel (+0.2°C/s en chauffe, -0.05°C/s à l'arrêt) |
| **Lampe (Lamp)** | `8082` | Gestion de l'état ON/OFF et de la luminosité (0-100%) |
| **Capteur de mouvement (Motion)** | `8083` | Détection de présence, déclenchement d'événements et de règles automatiques |

```mermaid
graph TD
    Browser["Navigateur / Dashboard Web<br>(localhost:8080)"]
    Gateway["Passerelle Gateway<br>(localhost:8080)"]
    Lamp["Lampe<br>(localhost:8082)"]
    Thermostat["Thermostat<br>(localhost:8081)"]
    Motion["Capteur Mouvement<br>(localhost:8083)"]

    Browser -->|HTTP REST + Bearer Token| Gateway
    Gateway -->|Flux SSE: GET /events/stream?token=...| Browser
    Gateway -->|Reverse Proxy: /things/{id}/**| Lamp
    Gateway -->|Reverse Proxy: /things/{id}/**| Thermostat
    Gateway -->|Reverse Proxy: /things/{id}/**| Motion

    Motion -->|POST /events: motion| Gateway
    Thermostat -->|POST /events: targetReached| Gateway
    Gateway -.->|Règles R1 & R2 directes| Lamp
    Gateway -.->|Règle R2 directe| Thermostat
```

---

## 🚀 2. Démarrage Rapide

Dans ton terminal, place-toi dans le dossier `starter-kit` :

```bash
cd starter-kit
```

### Lancer tous les services
Une seule commande suffit pour compiler (si nécessaire) et démarrer les 4 microservices en tâche de fond :

```bash
./run.sh
```

> **Astuces :**  
> * **Redémarrage instantané** sans recompiler Maven : `SKIP_BUILD=1 ./run.sh`  
> * **Ajuster le délai d'extinction de la lampe** (ex. 30s au lieu de 10s) : `T1=30 ./run.sh`  
> * **Arrêter proprement tous les services** : presse simplement **`Ctrl + C`**.

---

## 🖥️ 3. Le Dashboard Web Interactif

Ouvre ton navigateur web sur :  
👉 **[http://localhost:8080/](http://localhost:8080/)**

### 🔑 A. Connexion & Sécurité
En haut à droite de l'en-tête du dashboard :
* Le champ **Token** est pré-rempli avec `operator-secret`.
* La pastille **Connected** (verte) confirme que le navigateur reçoit les flux temps réel via **Server-Sent Events (SSE)** (`GET /events/stream`).
* **Test des rôles** :
  * Avec `operator-secret` : accès complet (lecture, écriture, déclenchement d'actions).
  * Avec `viewer-secret` : accès en lecture seule. Toute tentative de modification renvoie **`403 Forbidden`**.
* Le bouton **API Spec** ouvre directement le contrat OpenAPI [`gateway.yaml`](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/starter-kit/gateway.yaml).

---

## 🧪 4. Scénarios & Fonctionnalités à Tester

### 💡 Scénario 1 : Pilotage de la Lampe
1. Sur la carte **Lamp**, bascule l'interrupteur à bascule pour l'allumer ou l'éteindre.
2. Déplace le curseur **brightness** : la luminosité change immédiatement et le halo lumineux s'adapte en direct.
3. Clique sur le bouton **Toggle** pour inverser son état.

---

### 🌡️ Scénario 2 : Simulation Thermique & Consigne
1. Observe la valeur de **Temperature** qui fluctue en temps réel grâce au moteur physique intégré au thermostat.
2. Change le **Mode** :
   * `heat` : la résistance chauffe, la température augmente d'environ **+0.2°C par seconde**.
   * `eco` : mode économique.
   * `off` : le chauffage s'arrête, la pièce refroidit lentement (**-0.05°C par seconde**).
3. Modifie la consigne **Target** (ex: 21°C). Dès que la température atteint la consigne, un événement SSE `targetReached` est automatiquement émis et le thermostat stoppe la chauffe !

---

### 🏃 Scénario 3 : Règle Automatique R1 (Éclairage Intelligent)
* **Principe** : Tout mouvement allume automatiquement la lampe. Si aucun nouveau mouvement n'est détecté pendant $T_1$ (10 secondes par défaut), la lampe s'éteint toute seule.
* **Procédure de test** :
  1. Éteins manuellement la lampe.
  2. Sur la carte **Motion Sensor**, clique sur **Simulate Motion** (ou appuie sur la touche **Espace**).
  3. **Résultat** : La pastille de présence s'illumine, la lampe **s'allume immédiatement**, et un compte à rebours de 10 secondes démarre.
  4. Patiente 10 secondes sans toucher à rien $\rightarrow$ la lampe **s'éteint automatiquement**.
  5. *(Si tu recliques sur "Simulate Motion" avant la fin des 10s, le timer est automatiquement réarmé).*

---

### 🔥 Scénario 4 : Règle Automatique R2 (Confort Thermique)
* **Principe** : Si un mouvement survient ET que la température actuelle est **inférieure à 19°C** :
  * Le thermostat passe automatiquement en mode `heat` avec une consigne fixée à 19°C.
  * Dès que les 19°C sont atteints, le thermostat repasse automatiquement en mode `off`.
* **Procédure de test** :
  1. Mets le thermostat en mode `off`.
  2. Vérifie ou attends que la température descende sous 19°C.
  3. Déclenche un mouvement via le bouton **Simulate Motion**.
  4. Le thermostat s'allume automatiquement en `heat` et s'éteindra dès l'atteinte des 19°C (`targetReached`).

---

### 📡 Scénario 5 : Console d'Événements Live
En bas du dashboard, la console **Live Events** affiche chaque événement intercepté sur le bus SSE de la passerelle :
* Détections de mouvement (`motion`)
* Changements d'état (`propertyChanged`)
* Cible de température atteinte (`targetReached`)
* Enregistrements / désenregistrements d'objets (`registry`)

---

## 💻 5. Utilisation via l'API REST (`curl`)

Tu peux également tout tester directement depuis un terminal via les endpoints de la passerelle :

#### 1. Découverte des objets connectés :
```bash
curl -s -H "Authorization: Bearer operator-secret" http://localhost:8080/things | jq
```

#### 2. Consulter la température actuelle :
```bash
curl -s -H "Authorization: Bearer operator-secret" http://localhost:8080/things/thermostat/properties/temperature | jq
```

#### 3. Allumer la lampe :
```bash
curl -s -H "Authorization: Bearer operator-secret" \
     -H "Content-Type: application/json" \
     -X PUT -d '{"value": true}' \
     http://localhost:8080/things/lamp/properties/on | jq
```

#### 4. Régler la luminosité à 75% :
```bash
curl -s -H "Authorization: Bearer operator-secret" \
     -H "Content-Type: application/json" \
     -X PUT -d '{"value": 75}' \
     http://localhost:8080/things/lamp/properties/brightness | jq
```

#### 5. Déclencher une détection de mouvement :
```bash
curl -s -H "Authorization: Bearer operator-secret" \
     -X POST http://localhost:8080/things/motion/actions/simulateMotion | jq
```

#### 6. Tester le rôle `viewer` (lecture seule, refus d'écriture) :
```bash
# Lecture autorisée (200 OK) :
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer viewer-secret" http://localhost:8080/things

# Écriture refusée (403 Forbidden) :
curl -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer viewer-secret" \
     -H "Content-Type: application/json" -X PUT -d '{"value": false}' \
     http://localhost:8080/things/lamp/properties/on
```

---

## ✅ 6. Validation Automatisée de Conformité

Pour exécuter la suite complète de tests vérifiant l'ensemble des routes, statuts HTTP et conventions imposés par le sujet :

```bash
cd starter-kit
VIEWER=viewer-secret bash check-routes.sh
```

Résultat attendu :
```
38 passed, 0 failed
```

---

## 🗂️ 7. Références & Fichiers Clés

* [`starter-kit/gateway/src/main/resources/static/index.html`](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/starter-kit/gateway/src/main/resources/static/index.html) : Code source du Dashboard Web.
* [`starter-kit/gateway.yaml`](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/starter-kit/gateway.yaml) : Spécification OpenAPI 3.1 complète.
* [`starter-kit/gateway/src/main/java/wot/gateway/EventsController.java`](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/starter-kit/gateway/src/main/java/wot/gateway/EventsController.java) : Règles d'automatisation R1 et R2.
* [`starter-kit/gateway/src/main/java/wot/gateway/TokenFilter.java`](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/starter-kit/gateway/src/main/java/wot/gateway/TokenFilter.java) : Authentification Bearer et rôles `operator` / `viewer`.
