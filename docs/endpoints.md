# HTTP Endpoints

HTTP endpoints return JSON. Live tracking data is delivered over WebSockets, while basic HTTP endpoints serve a number
of supporting functions as documented below.

## Units of measurement

The following units of measure are used in JSON responses where applicable:

| Measurement | Unit
|-------------|-----
| Speed | Knots
| Depth | Meters
| Distance (old) | Millimeters
| Distance (new) | Meters
| Draft | Meters
| Temperature | Celsius

All units of measure are represented as JSON numbers.

## GET /tracks

Returns tracks driven:

    {
        "tracks": [
            {
                "track": 123,
                "trackName": "abc",
                "boat": 12,
                "boatName": "Her Highness",
                "points": 42,
                "duration": 3500,
                "distance": 1234,
                "topSpeed": 24.1,
                "avgSpeed": 23.2,
                "avgWaterTemp": 6.1,
                "topPoint": {
                    "id": 123,
                    "coord": {
                        "lng": 60.24,
                        "lat": 24.1
                    },
                    "speed": 24.1
                }
            }
        ]
    }

## PUT /tracks/:track_name

Modifies the title of the given track:

    {
        "title": "Evening boating"
    }

Provide the track name in the URL.

## PATCH /tracks/:track_id

Modifies the comments of the given track:

    {
        "comments": "Rainy and delightful!"
    }

## GET /users/me

Returns user information including any boats:

    {
        "user": {
            "id": 123,
            "username": "jack",
            "email": "jack@example.com",
            "boats": [
                {
                    "id": 12,
                    "name: "Her Highness",
                    "token": "abc123"
                }
            ],
            "enabled": true
        }
    }

## PUT /users/me

Changes the user's language. Use the following payload:

    {
        "language": "language_code_here"
    }
    
The following language codes are supported:

| Code | Language
|------|---------
| sv-SE | Swedish
| fi-FI | Finnish
| en-US | English

## PATCH /boats/:boat_id

Changes the name of the given boat:

    {
        "boatName": "My Lady"
    }

Obtain boat IDs using the */users/me* endpoint above.

## POST /users/notifications

Subscribes to push notifications:

    {
        "token": "device_token",
        "device": "ios"
    }

Key *device* must be one of:

| Value | Meaning
|-------|---------
| ios | iOS token
| android | Android token

## POST /users/notifications/disable

Unsubscribes from push notifications:

    {
        "token": "device_token",
    }

## GET /routes/:srclat/:srclng/:destlat/:destlng

Returns the shortest route between two points along fairways. Provide the source and destination
coordinates in the request.

Example request:

    GET /routes/60.14729/24.85396/60.11478/24.87489
    
Example response:

    {
        "from": {
            "lat": 60.14729290768696,
            "lng": 24.853965806435724
        },
        "to": {
            "lat": 60.1147881804653,
            "lng": 24.87489090627807
        },
        "totalCost": 5498.050648111757,
        "duration": 0.355,
        "route": {
            "cost": 4770.0589143530415,
            "links": [
                {
                    "cost": 0,
                    "to": {
                        "lat": 60.149,
                        "lng": 24.8534
                    }
                },
                {
                    "cost": 1400.7671712474576,
                    "to": {
                        "lat": 60.137,
                        "lng": 24.8611
                    }
                },
                ...
                {
                    "cost": 670.6786486135463,
                    "to": {
                        "lat": 60.119,
                        "lng": 24.8702
                    }
                }
            ]
        }
    }
    
The response contains the following items:

| Key | Type | Meaning
|-----|------|---------
| totalCost | Number | Distance in meters from source to destination, including the distance from the provided coordinates to nearby fairways
| route.cost | Number | Distance in meters along fairways
| route.links | Array | The route as an array of hops along fairway points

## POST /users/boats
 
Boat owners may invite other users to access the data of their boat:

To invite user 123 to access boat 14:

    {
        "operation": "grant",
        "boat": "14",
        "user": "123"
    }

To revoke access:

    {
        "operation": "revoke",
        "boat": "14",
        "user": "123"
    }

## POST /users/boats/answers

Invitees may accept or reject invites to the data of a given boat:

    {
        "boat": "14",
        "state": "accepted"
    }

Key `state` must be one of:

- accepted
- rejected

## WebSocket /ws/updates

Clients (web, iOS, Android) receive live boat updates using this WebSocket endpoint. The JSON-formatted messages use
the following general format:

    {
        "event": "event_type_here",
        "body": { ... event-specific body goes here ... }
    }

The following event types are used, with examples below:

| Event | Meaning
|-------|---------
| coords | Updated coordinates for boats
| vessels | Updated AIS location data for vessels
| ping | Ping event (can be ignored)

### Updated coordinates

Example JSON:

    {
        "event": "coords",
        "body": {
            "coords": [
                {
                    "coord": {
                        "lng": 60.24,
                        "lat": 24.1
                    },
                    "boatTime": "",
                    "speed": 41.1,
                    "waterTemp": 11.2,
                    "depth": 5000
                }
            ],
            "from": {
                "track": "123",
                "trackName": "abc",
                "trackTitle": "Nice ride",
                "canonical": "nice-ride",
                "boat:": "456",
                "boatName": "Amina",
                "username": "jack",
                "points": 42,
                "duration": 3500,
                "distance": 1234,
                "topSpeed": 24.1,
                "avgSpeed": 23.2,
                "avgWaterTemp": 6.1
            }
        }
    }

### Updated vessel locations

Example JSON:

    {
        "event": "vessels",
        "body": {
            "vessels": [
                {
                    "mmsi": 123456,
                    "name": "Amina",
                    "shipType": 80,
                    "coord": {
                        "lng": 60.24,
                        "lat": 24.1
                    },
                    "sog": 9.8,
                    "cog": 123.1,
                    "draft": 8.7,
                    "destination": "TALLINN",
                    "eta": 12,
                    "timestampMillis": 123456789,
                    "timestampFormatted": ""
                }
            ]
        }
    }

### Ping event

Example JSON:

    {
        "event": "ping",
        "body": {
            "sent": 123456789
        }
    }

## WebSocket /ws/boats

Agents send NMEA 0183 sentences to the backend using this endpoint.

The JSON-formatted messages must be of the following format:

    {
      "sentences": [
        "$GPGGA,174239,6110.2076,N,06450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
        "$GPZDA,141735,04,05,2018,-03,00*69"
      ]
    }

Provide the newest sentences last in the *sentences* array.

The server may send *ping* events to the agent over the socket at any time:

    {
        "event": "ping",
        "body": {
            "sent": 123456789
        }
    }
