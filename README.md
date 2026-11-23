# maestro

This project uses Quarkus, the Supersonic Subatomic Java Framework.

Administrator endpoint of the Treasure inc. project.

plugin quarkus used :
- quarkus-resteasy-reactive-jackson
- quarkus-hibernate-reactive-panache
- quarkus-reactive-pg-client
- quarkus-redis-client
- quarkus-smallrye-health
- quarkus-micrometer-registry-prometheus
- quarkus-mailer
- quarkus-scheduler
- quarkus-config-yaml

## Authentification

Tous les endpoints REST de Maestro (`/users`, `/reporting`, `/admin`…) passent par `security/ApiFilter` :
chaque requête doit porter l'en-tête

```
X-API-KEY: <valeur de maestro.apikey>
```

Sans cet en-tête, ou avec une mauvaise valeur, la réponse est `401 Unauthorized`.
Les endpoints techniques de Quarkus (`/q/health`, `/q/metrics`) ne sont **pas** servis sur ce port :
ils sont sur le port de management `9000` (`quarkus.management`, `9001` en test), qu'aucun Service ni Ingress n'expose.
Sur le port public `8082`, `/q/*` répond `404`. Ils restent joignables depuis le pod
(sondes Kubernetes, `kubectl port-forward deploy/treasure-inc-maestro 9000`, futur scraping Prometheus interne).

Pour l'interface d'administration, `GET /admin/health` renvoie le même rapport que `/q/health`
(`200` si tout est `UP`, `503` sinon), derrière la clé d'API.

## Administration de la production (`/admin`)

### D'où viennent les données

Le moteur (`TreasureIncENGINE_rebirth`) garde l'état chaud des joueurs dans **Redis** et l'écrit plus tard dans **PostgreSQL** (cache « write-behind ») :

- chaque joueur connecté a une session dans le hash Redis `treasure_inc.users.logged` (clé publique → JSON),
  avec `lastAction` (dernière action), `lastSync` (dernière écriture en base) et `dirty` (des changements attendent d'être écrits) ;
- un job du moteur (toutes les 30 s) écrit en base les sessions `dirty` dont la dernière écriture date de plus de 3 min,
  et retire de Redis les sessions inactives depuis plus d'1 h ;
- le moteur compte les requêtes qu'il reçoit dans Redis, par tranche de 10 s (`treasure_inc.stats.requests.<epochSeconde / 10>`, expiration 2 min).

Maestro lit Redis **sans jamais y écrire** (sauf pour envoyer une requête au moteur) et lit PostgreSQL : même les cadeaux sont écrits en base par le moteur.

### `POST /admin/sync` — forcer la synchronisation Redis → PostgreSQL

Demande au moteur d'écrire **tout de suite** en base chaque session qui a des changements en attente, sans attendre le délai habituel. Rien n'est retiré de Redis.

**Pourquoi c'est le moteur qui synchronise.** Maestro a accès à Redis et à PostgreSQL, mais la synchronisation n'est pas une simple copie :
elle doit prendre le verrou Redis du joueur, relire une copie fraîche de la session, insérer les objets générés avec leur id, réconcilier ceux vendus pendant l'écriture, écrire l'équipement et l'expérience des personnages, puis marquer la session comme synchronisée.
Toute cette logique est dans `CacheSyncService` du moteur ; la recopier dans Maestro la dupliquerait et risquerait de corrompre les données à la moindre évolution du format.
Maestro envoie donc la requête `forceSync` au moteur, sur le canal Redis des requêtes (`message-log`, comme l'API) et attend la réponse sur `message-log-res`.
L'API ne relaie que les requêtes du jeu : un joueur ne peut pas déclencher `forceSync`.

Réponse `200` :

```json
{
  "startedAt": "2026-11-20T10:00:00.123Z",
  "durationMs": 37,
  "syncedUsers": 4,
  "errors": 0
}
```

- `startedAt` : début de la synchronisation dans le moteur (ISO-8601) ;
- `durationMs` : sa durée dans le moteur, en millisecondes ;
- `syncedUsers` : nombre de joueurs écrits en base ;
- `errors` : nombre de joueurs qui n'ont pas pu être écrits (ils restent en attente et seront repris par le job du moteur).

Erreurs : `504` `{"message": …}` si aucun moteur ne répond dans `maestro.engine.sync-timeout` (30 s par défaut),
`502` `{"message": …}` si le moteur répond par une erreur, `401` sans clé d'API.

### `POST /admin/gifts` — envoyer un cadeau aux joueurs

Met un cadeau dans la **boîte à cadeaux** des joueurs (dans le jeu, le bouton cadeau à côté de celui des quêtes) :
un intitulé, et des gemmes, de l'or et/ou des objets que le joueur récupère quand il ouvre le coffre du cadeau.
Comme pour la synchronisation, c'est le moteur qui fait le travail : Maestro relaie le corps de la requête au moteur
(requête `sendGift` sur le canal Redis), qui le vérifie et écrit une ligne par joueur dans la table `gifts`, en une
seule requête SQL. Voir « Boîte à cadeaux » dans le README du moteur.

```json
{
  "title": "Lancement du jeu : profitez de ces gemmes gratuites !",
  "reward": { "gems": 1000, "money": 5000, "items": 3, "rarity": "epic" },
  "players": { "activeDays": 30 },
  "expiresInDays": 14
}
```

| Champ | Obligatoire | Sens |
|---|---|---|
| `title` | oui | l'intitulé lu par le joueur, 120 caractères au plus |
| `reward.gems` | non | gemmes données (0 à 100 000 000) |
| `reward.money` | non | or donné (0 à 100 000 000) |
| `reward.items` | non | nombre d'objets générés à la récupération (0 à 100) |
| `reward.rarity` | non | rareté imposée de ces objets : `common`, `uncommon`, `rare`, `epic`, `legendary` ; sans elle chaque objet est tiré comme dans un coffre |
| `players` | non | **sans lui, tous les joueurs** reçoivent le cadeau ; ses critères se cumulent (ET) |
| `players.activeDays` | non | joueurs actifs au moins une fois depuis `x` jours (dernière connexion ou dernière écriture de leur session en base) |
| `players.inactiveDays` | non | joueurs absents depuis au moins `x` jours, pour les faire revenir |
| `players.createdDays` | non | joueurs inscrits depuis `x` jours au plus, pour accueillir les nouveaux |
| `players.publicKeys` | non | liste de clés publiques (11 chiffres, 10 000 au plus) : ces joueurs seulement |
| `expiresInDays` | non | le cadeau expire après ce nombre de jours (1 à 3650) ; sans lui il n'expire jamais |

Il faut au moins une gemme, une pièce ou un objet.

Réponse `200` : l'identifiant de l'envoi, écrit sur chaque cadeau (colonne `gifts.batch`), et le nombre de joueurs qui
l'ont reçu.

```json
{ "batch": "4b0c6f1e-7d0a-4e57-9b8e-2f3c1d9a6e21", "sent": 1532 }
```

Erreurs : `400` `{"message": …}` quand le cadeau est invalide (la raison donnée par le moteur), `504` si aucun moteur
ne répond dans `maestro.engine.gift-timeout` (30 s par défaut), `502` si le moteur échoue, `401` sans clé d'API.

### `GET /admin/settings`, `PUT /admin/settings/maintenance`, `PUT /admin/settings/min-client-version` — maintenance et version du client

Les réglages du jeu sont gardés par le moteur (table `app_settings`, recopiée dans la clé Redis `treasure_inc.settings`
que lit l'API) : Maestro les lit et les change par les requêtes `getSettings`, `setMaintenance` et `setMinClientVersion`
du moteur. Les trois endpoints répondent les réglages :

```json
{
  "maintenance": { "enabled": true, "active": true, "reason": "Mise à jour du serveur", "start": null, "end": "2026-11-22T12:00:00Z" },
  "minClientVersion": "0.22",
  "updatedAt": "2026-11-22T10:00:00Z"
}
```

- `PUT /admin/settings/maintenance` `{enabled, reason, start, end}` : `reason` (500 caractères au plus) est obligatoire
  pour activer ; `start` et `end` (ISO-8601) sont optionnels. Activée, la maintenance s'applique de `start` (tout de
  suite sans lui) à `end` (jusqu'à ce qu'on la coupe sans lui) : `active` dit si elle est en cours. Pendant ce temps
  l'API et le moteur refusent les requêtes du jeu (`503` avec la raison) ; l'administration reste servie.
- `PUT /admin/settings/min-client-version` `{version}` : la plus ancienne version du client Unity
  (`Application.version`, ex. `0.22` ; `0` : toutes) qui peut se connecter. L'API la donne dans `/healthcheck`.

Erreurs : `400` `{"message": …}` si le moteur refuse la valeur, `504` sans réponse du moteur dans
`maestro.engine.settings-timeout` (10 s), `502` s'il répond par une erreur.

### `GET /admin/status` — résumé de production

Réponse `200` (contrat figé, utilisé par la page d'administration du site web) :

```json
{
  "generatedAt": "2026-11-20T10:00:00.456Z",
  "connectedUsers": 12,
  "desyncedUsers": 3,
  "redisMemoryMb": 4.21,
  "requestsPerMinute": 240.0,
  "extra": {
    "totalUsers":        { "label": "Joueurs inscrits",                      "value": 1532, "unit": "joueurs" },
    "newUsersToday":     { "label": "Nouveaux inscrits aujourd'hui",         "value": 17,   "unit": "joueurs" },
    "oldestPendingSync": { "label": "Plus ancienne donnée non synchronisée", "value": 95,   "unit": "s" }
  }
}
```

Toutes les clés sont toujours présentes : une valeur qui ne peut pas être calculée (Redis ou PostgreSQL injoignable) vaut `null`, et les autres sont tout de même renvoyées.

| Champ | Définition |
|---|---|
| `generatedAt` | Instant du calcul (ISO-8601, UTC). |
| `connectedUsers` | Joueurs **connectés** : sessions Redis dont la dernière action (`lastAction`) date de moins de `maestro.status.connected-window` (5 min par défaut). Une session reste dans Redis jusqu'à 1 h après la dernière action : la présence de la clé seule ne veut pas dire que le joueur est en jeu. |
| `desyncedUsers` | Joueurs **désynchronisés** : sessions Redis marquées `dirty`, c'est-à-dire avec des changements (solde, objets, personnages) pas encore écrits dans PostgreSQL. |
| `redisMemoryMb` | `used_memory` de `INFO memory`, en Mo (1 Mo = 1 048 576 octets), arrondi à 2 décimales. |
| `requestsPerMinute` | Requêtes reçues par le **moteur** (toutes instances) sur la dernière minute complète : somme des 6 tranches de 10 s qui précèdent la tranche en cours (donc avec au plus 10 s de retard). C'est la charge du moteur et non celle de Maestro : toute action d'un joueur passe par l'API puis par le moteur, alors que Maestro ne reçoit que les appels d'administration. Les `healthcheck` de l'API comptent aussi. Vaut `0` si le moteur ne publie pas encore ce compteur (version antérieure). |

Les trois métriques de `extra` :

| Clé | Libellé | Définition | Pourquoi |
|---|---|---|---|
| `totalUsers` | Joueurs inscrits | `count(*)` de la table `users`. | La taille de la base de joueurs, le repère de tous les autres chiffres. |
| `newUsersToday` | Nouveaux inscrits aujourd'hui | Joueurs dont `createdat` est postérieur à minuit dans le fuseau `maestro.status.zone` (Europe/Paris par défaut, comme les quêtes du moteur). | La création d'un compte est écrite tout de suite en base : chiffre exact, qui montre l'acquisition du jour. |
| `oldestPendingSync` | Plus ancienne donnée non synchronisée | Parmi les sessions `dirty`, l'âge (en s) de la plus ancienne dernière écriture en base (`now - lastSync`) ; `0` si rien n'est en attente. C'est un majorant de l'ancienneté des données qui seraient perdues si Redis tombait. | L'alerte de prod la plus utile sur ce cache : en temps normal elle reste sous ~3 min 30 (délai `max-age` + intervalle du job). Au-delà, la synchronisation du moteur est en panne ou en retard. |

Écartées : les joueurs actifs sur 24 h (`lastLogin` n'est mis à jour en base qu'à la connexion qui recharge la session, le chiffre serait faux pour les joueurs restés en cache), la latence ou le taux d'erreurs (le moteur ne les publie pas encore), l'uptime (déjà dans `/q/health` et Kubernetes).

### Métriques Prometheus

Les mêmes valeurs sont publiées sous forme de jauges sur `/q/metrics` (`maestro_users_connected`, `maestro_users_desynced`, `maestro_redis_memory_mb`,
`maestro_engine_requests_per_minute`, `maestro_users_total`, `maestro_users_new_today`, `maestro_sync_oldest_pending_seconds`).
Une jauge est lue de façon synchrone : elle ne doit pas interroger Redis ou PostgreSQL (clients réactifs). Elle lit donc le dernier résumé, recalculé toutes les `maestro.metrics.refresh` (60 s par défaut) ; une valeur inconnue vaut `NaN`.

### Configuration

| Propriété | Défaut | Rôle |
|---|---|---|
| `quarkus.redis.hosts` / `quarkus.redis.password` | `redis://localhost:6379` | Le Redis **du moteur** (variables `QUARKUS_REDIS_HOSTS`, `QUARKUS_REDIS_PASSWORD`). |
| `maestro.engine.channels.requests` / `.replies` | `message-log` / `message-log-res` | Canaux pub/sub du moteur (`engine.channels`). |
| `maestro.engine.sync-timeout` | `30s` | Attente maximale de la réponse à `POST /admin/sync`. |
| `maestro.engine.gift-timeout` | `30s` | Attente maximale de la réponse à `POST /admin/gifts`. |
| `maestro.engine.settings-timeout` | `10s` | Attente maximale des réponses de `/admin/settings`. |
| `maestro.status.connected-window` | `5m` | Fenêtre d'activité d'un joueur « connecté ». |
| `maestro.status.zone` | `Europe/Paris` | Fuseau du « aujourd'hui » de `newUsersToday`. |
| `maestro.metrics.refresh` | `60s` | Rafraîchissement des jauges Prometheus. |

### Exemples

```shell
curl -H "X-API-KEY: $MAESTRO_API_KEY" http://localhost:8082/admin/status
curl -X POST -H "X-API-KEY: $MAESTRO_API_KEY" http://localhost:8082/admin/sync
curl -X POST -H "X-API-KEY: $MAESTRO_API_KEY" -H "Content-Type: application/json" http://localhost:8082/admin/gifts \
  -d '{"title": "Lancement du jeu : profitez de ces gemmes gratuites !", "reward": {"gems": 1000}}'
curl -X PUT -H "X-API-KEY: $MAESTRO_API_KEY" -H "Content-Type: application/json" http://localhost:8082/admin/settings/maintenance \
  -d '{"enabled": true, "reason": "Mise à jour du serveur", "end": "2026-11-22T12:00:00Z"}'
```

Les requêtes Bruno correspondantes sont dans `treasure-inc-conf/rest/T_INC/maestro` (`admin status`, `admin sync`, `admin gifts`).

## Tests

Les tests `@QuarkusTest` utilisent PostgreSQL et Redis : par défaut ceux des dev services (Docker).
Si Testcontainers ne trouve pas Docker, on peut les lancer contre des conteneurs démarrés à la main :

```shell
docker run -d --rm --name maestro-test-pg -e POSTGRES_USER=test -e POSTGRES_PASSWORD=test -e POSTGRES_DB=test -p 55432:5432 postgres:16-alpine
docker run -d --rm --name maestro-test-redis -p 56379:6379 redis:7-alpine
./mvnw test -Dquarkus.datasource.db-kind=postgresql -Dquarkus.datasource.devservices.enabled=false \
  -Dquarkus.redis.devservices.enabled=false -Dquarkus.datasource.reactive.url=postgresql://localhost:55432/test \
  -Dquarkus.datasource.username=test -Dquarkus.datasource.password=test -Dquarkus.redis.hosts=redis://localhost:56379
```
