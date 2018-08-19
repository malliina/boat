# agent

Run this agent software on a small computer and connect it to your chartplotter.

This agent runs as a Systemd service and exposes an HTTP server on port 8080. It is tested on a Raspberry Pi 3 running
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
      "token": "abcd1234",
      "enabled": true
    }

Optionally, sign in to the iOS app to obtain a token. A token assigns any NMEA 0183 messages to the user with
the given token, and subsequently the user can view any tracks recorded with the given token from the iOS app or web
interface.
