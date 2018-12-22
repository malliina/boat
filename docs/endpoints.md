# Endpoints

HTTP endpoints return JSON.

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

## GET /tracks

Returns tracks driven:

    {
        "tracks": [
            {
                "track": 123,
                "trackName": "abc",
                "boat": 12,
                "boatName": "Her Highness",
                "user": 123,
                "username": "jack",
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

The following units are used:

| Key | Unit
|-----|------
| duration | seconds
| distance | meters
| speed | knots
| topSpeed | knots
| avgSpeed | knots
| avgWaterTemp | celsius

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
