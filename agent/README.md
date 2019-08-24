# agent

Run the agent software on a small computer and connect it to your chartplotter.

The agent runs as a Systemd service and exposes an HTTP server on port 8080. It is tested on a Raspberry Pi 3 running
Raspbian.

## Installation
 
1. Download the latest version from https://www.boat-tracker.com/files
1. Install the package: `dpkg -i <boat-agent_x.x.x_all.deb>`
1. Open a web browser and connect to the HTTP server on port 8080
1. In the web form, input the IP address and HTTP port of your chartplotter
1. Optionally, input the boat token available in the iOS app
1. Connect this agent to the same network as your chartplotter
1. The agent establishes a TCP connection to your chartplotter and receives NMEA 0183 messages

Alternatively, you can configure the plotter IP/port -combination in a configuration file set by the `conf.dir` system
property containing the following format:

    {
      "host": "10.0.0.1",
      "port": 10033,
      "device": "boat",
      "token": "abcd1234",
      "enabled": true
    }

Key *device* is one of:

- boat
- gps

Optionally, sign in to the iOS app to obtain a boat token. A token assigns any NMEA 0183 messages to the user with
the given token, and subsequently the user can view any tracks recorded with the given token from the iOS app or web
interface.

## Custom implementations

If this agent is not good enough for you, you can send NMEA 0183 sentences to boat-tracker.com using the HTTP API 
directly.

1. Open a WebSocket to https://www.boat-tracker.com/ws/boats
1. Optionally, provide your boat token in header `X-Token` when opening the WebSocket.
1. Send NMEA 0183 sentences over the socket as JSON-formatted text messages

The JSON messages must be of the following format:

    {
      "sentences": [
        "$GPGGA,174239,6110.2076,N,06450.5518,E,1,12,0.50,0,M,19.5,M,,*63",
        "$GPZDA,141735,04,05,2018,-03,00*69"
      ]
    }

## GPS modules

To set up a [GPS module](https://www.aliexpress.com/item/32913026283.html) on a Raspberry Pi:

1. Connect the GPS module to your Raspberry Pi over USB.

1. Install gpsd:

        sudo apt-get install gpsd gpsd-clients python-gps

1. Create file `/etc/systemd/system/gpsd.socket.d/socket.conf` with the following contents:

        [Socket]
        ListenStream=
        ListenStream=/var/run/gpsd.sock
        ListenStream=2947

1. Open a TCP connection to port 2947 and send the following message:

        ?WATCH={"enable":true,"json":false,"nmea":true,"raw":0,"scaled":false,"timing":false,"split24":false,"pps":false}

1. Your client should now receive NMEA0183 sentences over the TCP socket.
