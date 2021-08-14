# Agent

Run the agent software on a small computer and connect it to your chartplotter.

The agent runs as a Systemd service and exposes an HTTP server on port 8080. It is tested on a Raspberry Pi 3 running
Raspbian.

## Installation

Have a Raspberry Pi available in your boat, power it up and make sure you can connect to it.

1. Download the [latest version](https://www.boat-tracker.com/files/boat-agent_0.4.0_all.deb) of the agent from [www.boat-tracker.com/files](https://www.boat-tracker.com/files)
1. SSH to your Raspberry Pi
1. Install the downloaded package: `dpkg -i <boat-agent_x.x.x_all.deb>`
1. Open a web browser and connect to the started HTTP server on port 8080
1. In the web form, input the IP address and HTTP port of your chartplotter
1. Optionally, input the boat token available in the iOS app
1. Connect this agent to the same network (WLAN or cable) as your chartplotter

Following this setup:

1. The agent establishes a TCP connection to your chartplotter and receives any emitted NMEA 0183 messages
1. The agent also opens a WebSocket to the backend at wss://www.boat-tracker.com
1. Sentences received from the chartplotter are sent over the WebSocket to the backend

Alternatively, you can configure the plotter IP/port -combination in a configuration file set by the `conf.dir` system
property containing the following format:

    {
      "host": "10.0.0.1",
      "port": 10033,
      "token": "abcd1234",
      "enabled": true
    }

Optionally, sign in to the iOS app to obtain a boat token. A token assigns any NMEA 0183 messages to the user with
the given token, and subsequently the user can view any sentences and tracks recorded with the given token from the iOS 
app or web interface.

## Custom implementations

If this agent is not good enough for you, you can send NMEA 0183 sentences to boat-tracker.com using the HTTP API 
directly.

1. Open a WebSocket to wss://www.boat-tracker.com/ws/boats
1. Optionally, provide your boat token in header `X-Token` when opening the WebSocket.
1. Send NMEA 0183 sentences over the socket as JSON-formatted text messages

The JSON messages must be of the following format:

    {
      "sentences": [
        "$GPGGA,174239,6110.2076,N,06450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
        "$GPZDA,141735,04,05,2018,-03,00*69"
      ]
    }
