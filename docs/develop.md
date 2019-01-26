# Develop

Develop [Boat-Tracker](https://www.boat-tracker.com) clients using this JSON API. 
The [iOS](https://itunes.apple.com/us/app/boat-tracker/id1434203398?ls=1&mt=8), Android and 
[web](https://www.boat-tracker.com) apps all use this API.

## Versioning

The JSON API is versioned. Specify the API version in the `Accept` HTTP header. The following versions are currently
supported:

- application/vnd.boat.v1+json
- application/vnd.boat.v2+json

The documentation covers the latest API version (v2), therefore use the following header:

    Accept application/vnd.boat.v2+json

## Authentication

Boat-Tracker uses Google's [OAuth 2.0](https://developers.google.com/identity/protocols/OpenIDConnect) authentication 
system. Clients must

1. Initiate the OAuth 2.0 flow with Google
1. Obtain an ID token upon successful authentication with Google
1. Deliver the ID token to the Boat-Tracker backend when making authenticated API calls

Set the ID token in the `Authorization` header under the `Bearer` scheme:

    Authorization: Bearer google_id_token_goes_here
    
## Errors

Error responses use the following JSON format:

    {
        "errors": [
            { 
                "message": "JWT expired.",
                "key": "token_expired" 
            }
        ]
    }

Error keys include but are not limited to:

| Key | Meaning
|-----|---------
| token_expired | The JWT has expired. The client should obtain a new one from Google and try again.
| input | The client provided invalid input. Check your inputs.
| generic | Most likely clients cannot recover from this.
