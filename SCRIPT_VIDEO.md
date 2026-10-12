# 🎬 Script Vidéo de Démonstration (3 minutes chrono)

**Projet :** Smart Lab (Web of Things) — Projet 3  
**Module :** Web/Data Services Based Integration  
**Durée cible :** Exactement **3 minutes** (180 secondes)  
**Format :** MP4 ou lien hébergé (conforme aux exigences de [project3.pdf](file:///Users/poutrainlouis/Code/Cours/WDS/projet3NLR4/project3.pdf), page 6)

---

## 🖥️ Mise en Place Technique Avant Enregistrement

* **Disposition d'écran recommandée (Split Screen 50/50) :**
  * **À gauche :** Fenêtre de terminal (fond sombre, police lisible taille 14-16pt), positionnée dans le dossier `starter-kit`.
  * **À droite :** Navigateur Web (Chrome / Firefox / Safari) prêt sur `http://localhost:8080/`.
* **Préparez un onglet ou bloc-notes avec les commandes prêtes à coller** (voir mémo en fin de document).
* **Vérification initiale :** Les services doivent être arrêtés avant de lancer l'enregistrement (`Ctrl + C` si déjà lancés).

---

## ⏱️ Découpage Temporel & Chemin de Fer

| Timecode | Durée | Étape du Sujet Officiel | Affordance / Concept WoT |
| :---: | :---: | :--- | :--- |
| **00:00 – 00:25** | 25s | **Démarrage & Auto-enregistrement** | Architecture 4 microservices & `POST /things` (201) |
| **00:25 – 00:50** | 25s | **Découverte (Find) & Modèle** | Répertoire dynamique `GET /things` & *Web Thing Model* |
| **00:50 – 01:25** | 35s | **Proxy (Access), Lecture/Écriture & Action** | Pattern *Auth Proxy*, synchronisation temps réel SSE |
| **01:25 – 02:00** | 35s | **Règle R1 (Compose)** | Éclairage réarmable sur mouvement (`PUT /properties/on`) |
| **02:00 – 02:35** | 35s | **Règle R2 (Compose)** | Chauffage conditionnel (<19°C), simulation physique & `targetReached` |
| **02:35 – 02:55** | 20s | **Sécurité (Share) & Rôles RBAC** | Jeton Bearer (401), Rôle `viewer` en lecture seule (403) |
| **02:55 – 03:00** | 05s | **Conclusion & Validation** | Exécution de `check-routes.sh` (38/38 PASS) |

---

## 🎙️ Script Détaillé (Visuel, Actions & Voix Off)

---

### [00:00 – 00:25] 1. Démarrage des Services & Auto-enregistrement

* **Ce qu'on voit à l'écran :**
  * Le terminal à gauche (dossier `starter-kit`).
  * Le navigateur à droite prêt à afficher `http://localhost:8080/`.
* **Ce qu'on fait :**
  Lancer la commande dans le terminal :
  ```bash
  ./run.sh
  ```
  *(Laisser défiler les logs : démarrage de la gateway sur 8080, puis thermostat 8081, lamp 8082, motion 8083).*
* **Texte Voix Off (ce que vous dites) :**
  > *"Bonjour. Nous vous présentons la démonstration du Smart Lab Web of Things.*  
  > *Notre architecture est composée de 4 microservices Spring Boot 3 autonomes : la passerelle centrale sur le port 8080, le thermostat sur le port 8081, la lampe sur le 8082 et le détecteur de mouvement sur le 8083.*  
  > *L'ensemble démarre en une seule commande grâce au script `run.sh`. Dès leur initialisation, les trois objets s'auto-enregistrent auprès de la passerelle via une requête `POST /things` munie de leur jeton Bearer, recevant une réponse 201 Created avec l'en-tête Location."*

---

### [00:25 – 00:50] 2. Find : Découverte & Web Thing Model

* **Ce qu'on voit à l'écran :**
  * Le Dashboard à droite affiche les 3 cartes d'équipements (`thermostat`, `lamp`, `motion`).
  * Dans le terminal, envoyer une requête `GET /things` puis `GET /model`.
* **Ce qu'on fait :**
  ```bash
  curl -s -H "Authorization: Bearer operator-secret" http://localhost:8080/things | jq .
  curl -s http://localhost:8082/model | jq .
  ```
* **Texte Voix Off :**
  > *"Au niveau Find, la passerelle agit comme annuaire central. La route `GET /things` liste l'ensemble des équipements enregistrés avec leurs hyperliens d'accès.*  
  > *Chaque objet s'auto-décrit sur sa route `GET /model` conformément au standard Web Thing Model du cours : on y retrouve ses métadonnées, le typage strict de ses propriétés, le schéma de ses actions et ses liens relatifs.*  
  > *Le Dashboard web s'est synchronisé instantanément dès la réception de l'événement SSE d'enregistrement."*

---

### [00:50 – 01:25] 3. Access : Auth Proxy, Lecture/Écriture & Action

* **Ce qu'on voit à l'écran :**
  * La carte **Lamp** sur le Dashboard.
  * Déplacement du slider de luminosité ou envoi d'un `PUT` de propriété, puis clic sur le bouton d'action **Toggle**.
* **Ce qu'on fait :**
  1. Régler la luminosité de la lampe à 75% via le curseur UI (ou via curl) :
     ```bash
     curl -s -X PUT -H "Authorization: Bearer operator-secret" -H "Content-Type: application/json" -d '{"value":75}' http://localhost:8080/things/lamp/properties/brightness
     ```
  2. Cliquer sur le bouton **Toggle** de la lampe sur le Dashboard (ou via curl) :
     ```bash
     curl -s -X POST -H "Authorization: Bearer operator-secret" http://localhost:8080/things/lamp/actions/toggle
     ```
* **Texte Voix Off :**
  > *"Pour le niveau Access, nous appliquons le pattern Auth Proxy : les clients n'interrogent jamais les objets en direct, tout transite par la passerelle.*  
  > *Ici, nous modifions la luminosité à 75% via `PUT /things/lamp/properties/brightness`. La passerelle relaie la commande à la lampe, qui émet un événement webhook. La passerelle le rediffuse en temps réel au dashboard via notre flux Server-Sent Events : la jauge et le halo s'actualisent sans aucun rechargement de page.*  
  > *Déclenchons maintenant l'action sans paramètre `toggle` : la lampe s'éteint immédiatement."*

---

### [01:25 – 02:00] 4. Compose : Règle R1 (Éclairage Intelligent Réarmable)

* **Ce qu'on voit à l'écran :**
  * La lampe est éteinte (`OFF`).
  * Sur la carte **Motion Sensor**, clic sur le bouton **Simulate Motion**.
  * Observer la lampe qui s'allume en direct.
  * Recliquer à 5 secondes pour montrer le réarmement.
  * Laisser s'écouler 10 secondes : la lampe s'éteint toute seule.
* **Ce qu'on fait :**
  1. Vérifier que la lampe est bien sur `OFF`.
  2. Clic sur **Simulate Motion** (ou dans le terminal) :
     ```bash
     curl -s -X POST -H "Authorization: Bearer operator-secret" http://localhost:8080/things/motion/actions/simulateMotion
     ```
  3. Attendre 5 secondes et recliquer sur **Simulate Motion** (réarmement du timer $T_1$).
  4. Attendre 10 secondes sans toucher : observer l'extinction automatique.
* **Texte Voix Off :**
  > *"Passons au niveau Compose avec la règle d'automatisation R1. La lampe étant éteinte, nous déclenchons une détection de présence.*  
  > *Le capteur poste l'événement `motion` à la passerelle. Le RulesEngine allume immédiatement la lampe par une écriture de sa propriété `on` à `true` — et non par `toggle` pour éviter d'éteindre une lampe déjà active. Un timer réarmable T1 de 10 secondes est enclenché.*  
  > *Si un nouveau mouvement survient avant l'échéance, le compte à rebours est réinitialisé. À l'expiration du délai sans détection, la passerelle éteint automatiquement la lampe."*

---

### [02:00 – 02:35] 5. Compose : Règle R2 (Régulation Thermique Multi-Conditions)

* **Ce qu'on voit à l'écran :**
  * La carte **Thermostat** affiche la température initiale de 17.5°C (< 19°C) en mode `off`.
  * Déclencher un mouvement (**Simulate Motion**).
  * Observer le mode passer à `heat`, la consigne à `19.0°C`, et la jauge de température grimper en temps réel (+0.2°C/s).
  * Dès que 19.0°C est atteint : l'événement `targetReached` apparaît dans les logs et le mode bascule automatiquement à `off`.
* **Ce qu'on fait :**
  1. Clic sur **Simulate Motion**.
  2. Suivre visuellement la montée : 17.5°C $\rightarrow$ 17.7°C $\rightarrow$ ... $\rightarrow$ 19.0°C.
  3. Constater l'arrêt automatique de la chauffe à 19.0°C.
* **Texte Voix Off :**
  > *"Démontrons à présent la règle R2 : régulation thermique multi-conditions.*  
  > *La température ambiante est actuellement de 17.5°C, donc inférieure au seuil de confort de 19°C. Lorsqu'un mouvement est détecté, la règle s'active : le thermostat bascule en mode `heat` avec une consigne à 19°C.*  
  > *Le moteur de simulation thermique interne fait monter la température de +0.2°C par seconde. Dès que 19°C est franchi par le bas, le thermostat émet l'événement `targetReached`. La passerelle intercepte cet événement et coupe immédiatement le chauffage en repassant le thermostat en mode `off`."*

---

### [02:35 – 02:55] 6. Share : Sécurité Bearer & Contrôle d'Accès RBAC

* **Ce qu'on voit à l'écran :**
  * Dans le terminal, envoyer une requête sans token (401), puis une écriture avec le token `viewer-secret` (403).
* **Ce qu'on fait :**
  ```bash
  # 1. Requête rejetée sans token (401 Unauthorized)
  curl -s -i http://localhost:8080/things | head -n 5

  # 2. Requête rejetée en écriture avec le rôle viewer (403 Forbidden)
  curl -s -i -X PUT -H "Authorization: Bearer viewer-secret" -H "Content-Type: application/json" -d '{"value":true}' http://localhost:8080/things/lamp/properties/on | head -n 5
  ```
* **Texte Voix Off :**
  > *"Au niveau Share, la sécurité est garantie par notre TokenFilter Bearer. Toute requête sans jeton sur l'API de la passerelle est refusée avec le statut 401 Unauthorized et un corps d'erreur JSON explicite.*  
  > *De plus, nous avons implémenté le bonus de contrôle d'accès basé sur les rôles : le jeton `viewer-secret` autorise la consultation en lecture seule, mais toute tentative d'écriture ou d'action est bloquée avec le statut 403 Forbidden."*

---

### [02:55 – 03:00] 7. Conclusion & Validation de la Suite de Tests

* **Ce qu'on voit à l'écran :**
  * Lancement du script de test automatisé `check-routes.sh`.
  * Affichage de la ligne finale : `38 passed, 0 failed`.
* **Ce qu'on fait :**
  ```bash
  VIEWER=viewer-secret ./check-routes.sh
  ```
* **Texte Voix Off :**
  > *"Pour conclure, la suite de tests officielle `check-routes.sh` valide avec succès 38 tests sur 38. L'ensemble du système, du contrat OpenAPI et des règles Web of Things est parfaitement opérationnel. Merci pour votre attention."*

---

## 📋 Mémo des Commandes Prêtes à Coller (Pense-Bête)

Pour répéter ou exécuter fluidement pendant l'enregistrement sans faute de frappe :

```bash
# 1. Lancement
./run.sh

# 2. Découverte (Find)
curl -s -H "Authorization: Bearer operator-secret" http://localhost:8080/things | jq .
curl -s http://localhost:8082/model | jq .

# 3. Access (Proxy)
curl -s -X PUT -H "Authorization: Bearer operator-secret" -H "Content-Type: application/json" -d '{"value":75}' http://localhost:8080/things/lamp/properties/brightness
curl -s -X POST -H "Authorization: Bearer operator-secret" http://localhost:8080/things/lamp/actions/toggle

# 4. R1 & R2 (Compose)
curl -s -X POST -H "Authorization: Bearer operator-secret" http://localhost:8080/things/motion/actions/simulateMotion

# 5. Sécurité (Share)
curl -s -i http://localhost:8080/things | head -n 5
curl -s -i -X PUT -H "Authorization: Bearer viewer-secret" -H "Content-Type: application/json" -d '{"value":true}' http://localhost:8080/things/lamp/properties/on | head -n 5

# 6. Tests complets
VIEWER=viewer-secret ./check-routes.sh
```
