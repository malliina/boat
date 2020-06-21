[![Build Status](https://github.com/malliina/boat/workflows/Test/badge.svg)](https://github.com/malliina/boat/actions)
[![Build Status](https://travis-ci.org/malliina/boat.png?branch=master)](https://travis-ci.org/malliina/boat)
[![Sponsored](https://img.shields.io/badge/chilicorn-sponsored-brightgreen.svg?logo=data%3Aimage%2Fpng%3Bbase64%2CiVBORw0KGgoAAAANSUhEUgAAAA4AAAAPCAMAAADjyg5GAAABqlBMVEUAAAAzmTM3pEn%2FSTGhVSY4ZD43STdOXk5lSGAyhz41iz8xkz2HUCWFFhTFFRUzZDvbIB00Zzoyfj9zlHY0ZzmMfY0ydT0zjj92l3qjeR3dNSkoZp4ykEAzjT8ylUBlgj0yiT0ymECkwKjWqAyjuqcghpUykD%2BUQCKoQyAHb%2BgylkAyl0EynkEzmkA0mUA3mj86oUg7oUo8n0k%2FS%2Bw%2Fo0xBnE5BpU9Br0ZKo1ZLmFZOjEhesGljuzllqW50tH14aS14qm17mX9%2Bx4GAgUCEx02JySqOvpSXvI%2BYvp2orqmpzeGrQh%2Bsr6yssa2ttK6v0bKxMBy01bm4zLu5yry7yb29x77BzMPCxsLEzMXFxsXGx8fI3PLJ08vKysrKy8rL2s3MzczOH8LR0dHW19bX19fZ2dna2trc3Nzd3d3d3t3f39%2FgtZTg4ODi4uLj4%2BPlGxLl5eXm5ubnRzPn5%2Bfo6Ojp6enqfmzq6urr6%2Bvt7e3t7u3uDwvugwbu7u7v6Obv8fDz8%2FP09PT2igP29vb4%2BPj6y376%2Bu%2F7%2Bfv9%2Ff39%2Fv3%2BkAH%2FAwf%2FtwD%2F9wCyh1KfAAAAKXRSTlMABQ4VGykqLjVCTVNgdXuHj5Kaq62vt77ExNPX2%2Bju8vX6%2Bvr7%2FP7%2B%2FiiUMfUAAADTSURBVAjXBcFRTsIwHAfgX%2FtvOyjdYDUsRkFjTIwkPvjiOTyX9%2FAIJt7BF570BopEdHOOstHS%2BX0s439RGwnfuB5gSFOZAgDqjQOBivtGkCc7j%2B2e8XNzefWSu%2BsZUD1QfoTq0y6mZsUSvIkRoGYnHu6Yc63pDCjiSNE2kYLdCUAWVmK4zsxzO%2BQQFxNs5b479NHXopkbWX9U3PAwWAVSY%2FpZf1udQ7rfUpQ1CzurDPpwo16Ff2cMWjuFHX9qCV0Y0Ok4Jvh63IABUNnktl%2B6sgP%2BARIxSrT%2FMhLlAAAAAElFTkSuQmCC)](http://spiceprogram.org/oss-sponsorship)

# boat-tracker

See [Documentation](https://docs.boat-tracker.com).

## Code

This repo contains:

- a boat agent in folder [agent](agent)
- server-side code in [backend](backend)
- frontend code in [frontend](frontend)
- shared code in [shared](shared)
- integration tests in [boat-test](boat-test)
- documentation in [docs](docs)

The server and frontend is deployed to [www.boat-tracker.com](https://www.boat-tracker.com/).

The iOS app is in repo [boattracker-ios](https://github.com/malliina/boattracker-ios).

The Android app is in repo [boattracker-android](https://github.com/malliina/boattracker-android).

## Deploying documentation

GitHub Pages hosts the documentation. To deploy:

    mkdocs gh-deploy

## Releasing the agent

To release a new version of the agent, run:

    sbt "project agent" release
    
To upload a new downloadable `.deb` binary to S3, run:

    sbt "project agent" buildAndUpload
    
This will make a binary available at [www.boat-tracker.com/files](https://www.boat-tracker.com/files).

Creating the binary requires a Linux operating system with certain packages installed,
therefore it is currently a separate step from the release process.

## Notes

### IntelliJ

If using BSP, exclude folder [project/target/node-modules](project/target/node-modules) in the 
project structure settings to reduce excessive indexing.

### Quill

Due to an apparent bug, don't use camelCase member names for Embedded case classes. Wrong column aliases are generated.

## License

Licensed under the 3-Clause BSD License.
