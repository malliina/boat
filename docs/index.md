# Develop

Develop Boat-Tracker clients using this JSON API.

## General

## Authentication

Boat-Tracker uses Google's [OAuth 2.0](https://developers.google.com/identity/protocols/OpenIDConnect) authentication 
system. Clients must

1. Obtain an ID token upon successful authentication with Google
1. Deliver the ID token to the Boat-Tracker backend when making authenticated API calls 

Set the ID token in the `Authorization` header under the `Bearer` scheme:

    Authorization: Bearer google_id_token_goes_here
    
## Errors

Error responses use the following JSON format:

    {
        "errors": [
            { "message": "This is an error message." }
        ]
    }
